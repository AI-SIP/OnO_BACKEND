package com.aisip.OnO.backend.studyroom.service;

import com.aisip.OnO.backend.common.emoji.CustomEmojiValidator;
import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.common.response.CursorPageResponse;
import com.aisip.OnO.backend.studyroom.dto.StudyRoomDtos.*;
import com.aisip.OnO.backend.studyroom.entity.*;
import com.aisip.OnO.backend.studyroom.event.StudyRoomActivityEvent;
import com.aisip.OnO.backend.studyroom.exception.StudyRoomErrorCase;
import com.aisip.OnO.backend.studyroom.repository.*;
import com.aisip.OnO.backend.user.entity.User;
import com.aisip.OnO.backend.user.exception.UserErrorCase;
import com.aisip.OnO.backend.user.repository.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;
import static org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudyRoomFeedService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final StudyRoomAccessService accessService;
    private final StudyRoomMemberRepository memberRepository;
    private final StudyRoomFeedRepository feedRepository;
    private final StudyRoomFeedReactionRepository reactionRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final StudyRoomReactionService reactionService;
    private final CustomEmojiValidator customEmojiValidator;
    private final StudyRoomChallengeService challengeService;

    @Transactional(readOnly = true)
    public CursorPageResponse<FeedItemResponse> getFeed(Long roomId, Long userId, Long cursor, int size) {
        accessService.validateMember(roomId, userId);
        int safeSize = Math.min(size, 50);
        List<StudyRoomFeed> feeds = feedRepository.findWithUserByRoomIdAndCursor(roomId, cursor, PageRequest.of(0, safeSize + 1));
        boolean hasNext = feeds.size() > safeSize;
        List<StudyRoomFeed> contentFeeds = hasNext ? feeds.subList(0, safeSize) : feeds;
        Map<Long, List<StudyRoomFeedReaction>> reactions = reactionRepository.findAllByFeedIds(
                        contentFeeds.stream().map(StudyRoomFeed::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(reaction -> reaction.getFeed().getId()));
        List<FeedItemResponse> content = contentFeeds.stream()
                .map(feed -> toFeedItem(feed, reactions.getOrDefault(feed.getId(), List.of()), userId))
                .toList();
        Long nextCursor = hasNext && !contentFeeds.isEmpty() ? contentFeeds.get(contentFeeds.size() - 1).getId() : null;
        return new CursorPageResponse<>(content, nextCursor, hasNext, safeSize);
    }

    @Transactional
    public FeedReactionToggleResponse toggleReaction(Long roomId, Long feedId, Long userId, ReactionToggleRequest request) {
        accessService.validateMember(roomId, userId);
        customEmojiValidator.validate(request.emoji());
        StudyRoomFeed feed = feedRepository.findByIdAndRoomId(feedId, roomId)
                .orElseThrow(() -> new ApplicationException(StudyRoomErrorCase.STUDY_ROOM_NOT_FOUND));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApplicationException(UserErrorCase.USER_NOT_FOUND));
        reactionRepository.findByFeedIdAndUserIdAndEmoji(feedId, userId, request.emoji())
                .ifPresentOrElse(reactionRepository::delete,
                        () -> reactionRepository.save(StudyRoomFeedReaction.create(feed, user, request.emoji())));
        List<ReactionResponse> reactions = reactionService.summarizeFeedReactions(reactionRepository.findAllByFeedId(feedId), userId);
        return new FeedReactionToggleResponse(feedId, reactions);
    }

    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Transactional(propagation = REQUIRES_NEW)
    public void handleActivity(StudyRoomActivityEvent event) {
        try {
            createFeedsForUserRooms(event.userId(), event.eventType(), event.metadata());
        } catch (Exception e) {
            log.error("Failed to create study room feed. userId: {}, eventType: {}", event.userId(), event.eventType(), e);
        }
        try {
            challengeService.checkAndNotifyChallengeCompletionForUser(event.userId());
        } catch (Exception e) {
            log.error("Failed to check challenge completion. userId: {}, eventType: {}", event.userId(), event.eventType(), e);
        }
    }

    @Transactional
    public void createFeedsForUserRooms(Long userId, StudyRoomFeedEventType eventType, Map<String, Object> metadata) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApplicationException(UserErrorCase.USER_NOT_FOUND));
        List<StudyRoomMember> memberships = memberRepository.findAllWithRoomByUserId(userId);
        for (StudyRoomMember membership : memberships) {
            createOrAccumulate(membership.getRoom(), user, eventType, metadata == null ? Map.of() : metadata);
        }
    }

    private void createOrAccumulate(StudyRoom room, User user, StudyRoomFeedEventType eventType, Map<String, Object> metadata) {
        if (eventType == StudyRoomFeedEventType.PROBLEM_REGISTERED) {
            LocalDate today = LocalDate.now(KST);
            LocalDateTime start = today.atStartOfDay();
            LocalDateTime end = today.atTime(LocalTime.MAX);
            Optional<StudyRoomFeed> existing = feedRepository
                    .findTopByRoomIdAndUserIdAndEventTypeAndCreatedAtBetweenOrderByCreatedAtDesc(
                            room.getId(), user.getId(), eventType, start, end);
            if (existing.isPresent()) {
                Map<String, Object> current = readMetadata(existing.get().getMetadataJson());
                int oldCount = ((Number) current.getOrDefault("count", 0)).intValue();
                int newCount = ((Number) metadata.getOrDefault("count", 1)).intValue();
                current.put("count", oldCount + newCount);
                existing.get().updateMetadataJson(writeMetadata(current));
                return;
            }
        }
        feedRepository.save(StudyRoomFeed.create(room, user, eventType, writeMetadata(metadata)));
    }

    private FeedItemResponse toFeedItem(StudyRoomFeed feed, List<StudyRoomFeedReaction> reactions, Long userId) {
        return new FeedItemResponse(
                feed.getId(),
                feed.getUser().getId(),
                feed.getUser().getName(),
                feed.getUser().getProfileImageUrl(),
                toApiValue(feed.getEventType().name()),
                readMetadata(feed.getMetadataJson()),
                feed.getCreatedAt(),
                reactionService.summarizeFeedReactions(reactions, userId)
        );
    }

    private Map<String, Object> readMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(metadataJson, MAP_TYPE);
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private String writeMetadata(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata == null ? Map.of() : metadata);
        } catch (Exception e) {
            throw new ApplicationException(StudyRoomErrorCase.INVALID_STUDY_ROOM_REQUEST);
        }
    }

    static String toApiValue(String enumName) {
        return enumName.toLowerCase(Locale.ROOT);
    }
}
