package com.aisip.OnO.backend.studyroom.service;

import com.aisip.OnO.backend.common.emoji.CustomEmojiValidator;
import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.common.response.CursorPageResponse;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.problem.entity.ProblemImageData;
import com.aisip.OnO.backend.problem.entity.ProblemImageType;
import com.aisip.OnO.backend.problem.service.ProblemService;
import com.aisip.OnO.backend.studyroom.dto.StudyRoomDtos.*;
import com.aisip.OnO.backend.studyroom.entity.*;
import com.aisip.OnO.backend.studyroom.event.StudyRoomActivityEvent;
import com.aisip.OnO.backend.studyroom.exception.StudyRoomErrorCase;
import com.aisip.OnO.backend.studyroom.repository.*;
import com.aisip.OnO.backend.user.entity.User;
import com.aisip.OnO.backend.user.exception.UserErrorCase;
import com.aisip.OnO.backend.user.repository.UserRepository;
import com.aisip.OnO.backend.util.fcm.dto.NotificationRequestDto;
import com.aisip.OnO.backend.util.fcm.service.FcmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudyRoomSharedProblemService {

    private final StudyRoomAccessService accessService;
    private final StudyRoomSharedProblemRepository sharedProblemRepository;
    private final StudyRoomSharedProblemReactionRepository reactionRepository;
    private final StudyRoomSharedProblemCommentRepository commentRepository;
    private final StudyRoomSharedProblemCommentReactionRepository commentReactionRepository;
    private final StudyRoomMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final ProblemService problemService;
    private final StudyRoomReactionService reactionService;
    private final ApplicationEventPublisher eventPublisher;
    private final CustomEmojiValidator customEmojiValidator;
    private final FcmService fcmService;

    @Transactional(readOnly = true)
    public CursorPageResponse<SharedProblemResponse> getSharedProblems(Long roomId, Long userId, Long cursor, int size) {
        accessService.validateMember(roomId, userId);
        int safeSize = Math.min(size, 50);
        List<StudyRoomSharedProblem> sharedProblems = sharedProblemRepository.findByRoomIdAndCursor(roomId, cursor, PageRequest.of(0, safeSize + 1));
        boolean hasNext = sharedProblems.size() > safeSize;
        List<StudyRoomSharedProblem> contentSharedProblems = hasNext ? sharedProblems.subList(0, safeSize) : sharedProblems;
        List<Long> sharedProblemIds = contentSharedProblems.stream()
                .map(StudyRoomSharedProblem::getId)
                .toList();
        Map<Long, List<StudyRoomSharedProblemReaction>> reactions = sharedProblemIds.isEmpty()
                ? Map.of()
                : reactionRepository.findAllBySharedProblemIds(sharedProblemIds).stream()
                .collect(Collectors.groupingBy(reaction -> reaction.getSharedProblem().getId()));
        Map<Long, Long> commentCounts = sharedProblemIds.isEmpty()
                ? Map.of()
                : commentRepository.countBySharedProblemIds(sharedProblemIds).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
        List<SharedProblemResponse> content = contentSharedProblems.stream()
                .map(sharedProblem -> toResponse(
                        sharedProblem,
                        reactions.getOrDefault(sharedProblem.getId(), List.of()),
                        commentCounts.getOrDefault(sharedProblem.getId(), 0L),
                        userId))
                .toList();
        Long nextCursor = hasNext && !contentSharedProblems.isEmpty()
                ? contentSharedProblems.get(contentSharedProblems.size() - 1).getId()
                : null;
        return new CursorPageResponse<>(content, nextCursor, hasNext, safeSize);
    }

    @Transactional
    public SharedProblemResponse shareProblem(Long roomId, Long userId, SharedProblemCreateRequest request) {
        accessService.validateMember(roomId, userId);
        if (request.problemId() == null || request.comment() != null && request.comment().length() > 100) {
            throw new ApplicationException(StudyRoomErrorCase.INVALID_STUDY_ROOM_REQUEST);
        }
        if (sharedProblemRepository.existsByRoomIdAndProblemId(roomId, request.problemId())) {
            throw new ApplicationException(StudyRoomErrorCase.ALREADY_SHARED_PROBLEM);
        }
        Problem problem = problemService.findProblemEntityWithImageData(request.problemId(), userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApplicationException(UserErrorCase.USER_NOT_FOUND));
        StudyRoomSharedProblem sharedProblem = sharedProblemRepository.save(
                StudyRoomSharedProblem.create(accessService.getRoomOrThrow(roomId), user, problem, request.comment()));
        eventPublisher.publishEvent(new StudyRoomActivityEvent(userId, StudyRoomFeedEventType.PROBLEM_SHARED,
                Map.of("reference", problem.getReference() == null ? "" : problem.getReference(),
                        "sharedProblemId", sharedProblem.getId())));
        notifyRoomMembers(roomId, userId,
                user.getName() + "님이 문제를 공유했어요",
                referenceOrFallback(problem.getReference()),
                Map.of("type", "SHARED_PROBLEM", "roomId", String.valueOf(roomId), "sharedProblemId", String.valueOf(sharedProblem.getId())));
        return toResponse(sharedProblem, List.of(), 0L, userId);
    }

    @Transactional
    public SharedProblemReactionToggleResponse toggleReaction(Long roomId, Long sharedProblemId, Long userId, ReactionToggleRequest request) {
        accessService.validateMember(roomId, userId);
        customEmojiValidator.validate(request.emoji());
        StudyRoomSharedProblem sharedProblem = sharedProblemRepository.findByIdAndRoomId(sharedProblemId, roomId)
                .orElseThrow(() -> new ApplicationException(StudyRoomErrorCase.SHARED_PROBLEM_NOT_FOUND));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApplicationException(UserErrorCase.USER_NOT_FOUND));
        boolean added;
        var existing = reactionRepository.findBySharedProblemIdAndUserIdAndEmoji(sharedProblemId, userId, request.emoji());
        if (existing.isPresent()) {
            reactionRepository.delete(existing.get());
            added = false;
        } else {
            reactionRepository.save(StudyRoomSharedProblemReaction.create(sharedProblem, user, request.emoji()));
            added = true;
        }
        Long sharerId = sharedProblem.getSharedByUser().getId();
        if (added && !sharerId.equals(userId)) {
            notifyUser(sharerId,
                    "공유 문제에 반응이 달렸어요",
                    user.getName() + "님이 " + request.emoji() + " 반응을 남겼어요.",
                    Map.of("type", "SHARED_PROBLEM_REACTION", "roomId", String.valueOf(roomId), "sharedProblemId", String.valueOf(sharedProblemId)));
        }
        return new SharedProblemReactionToggleResponse(sharedProblemId,
                reactionService.summarizeSharedProblemReactions(reactionRepository.findAllBySharedProblemId(sharedProblemId), userId));
    }

    @Transactional
    public void deleteSharedProblem(Long roomId, Long sharedProblemId, Long userId) {
        accessService.validateMember(roomId, userId);
        StudyRoomSharedProblem sharedProblem = sharedProblemRepository.findByIdAndRoomId(sharedProblemId, roomId)
                .orElseThrow(() -> new ApplicationException(StudyRoomErrorCase.SHARED_PROBLEM_NOT_FOUND));
        if (!sharedProblem.getSharedByUser().getId().equals(userId)) {
            throw new ApplicationException(StudyRoomErrorCase.STUDY_ROOM_FORBIDDEN);
        }
        commentReactionRepository.deleteBySharedProblemId(sharedProblemId);
        commentRepository.deleteBySharedProblemId(sharedProblemId);
        reactionRepository.deleteBySharedProblemId(sharedProblemId);
        sharedProblemRepository.delete(sharedProblem);
    }

    private SharedProblemResponse toResponse(StudyRoomSharedProblem sharedProblem,
                                             List<StudyRoomSharedProblemReaction> reactions,
                                             long commentCount,
                                             Long userId) {
        Problem problem = sharedProblem.getProblem();
        return new SharedProblemResponse(
                sharedProblem.getId(),
                sharedProblem.getSharedByUser().getId(),
                sharedProblem.getSharedByUser().getName(),
                sharedProblem.getSharedByUser().getProfileImageUrl(),
                problem.getId(),
                problemImageUrl(problem),
                problemImageUrls(problem),
                referenceOrFallback(problem.getReference()),
                sharedProblem.getComment(),
                commentCount,
                sharedProblem.getCreatedAt(),
                reactionService.summarizeSharedProblemReactions(reactions, userId)
        );
    }

    private String referenceOrFallback(String reference) {
        return reference == null || reference.isBlank() ? "공유 문제" : reference;
    }

    private List<String> problemImageUrls(Problem problem) {
        return problem.getProblemImageDataList().stream()
                .filter(image -> image.getProblemImageType() == ProblemImageType.PROBLEM_IMAGE)
                .map(ProblemImageData::getImageUrl)
                .toList();
    }

    private String problemImageUrl(Problem problem) {
        return problemImageUrls(problem).stream()
                .findFirst()
                .orElse(null);
    }

    private void notifyRoomMembers(Long roomId, Long excludeUserId, String title, String body, Map<String, String> data) {
        NotificationRequestDto dto = new NotificationRequestDto(null, title, body, data);
        memberRepository.findAllWithUserByRoomId(roomId).forEach(member -> {
            Long memberId = member.getUser().getId();
            if (!memberId.equals(excludeUserId)) {
                notifyUser(memberId, dto);
            }
        });
    }

    private void notifyUser(Long userId, String title, String body, Map<String, String> data) {
        notifyUser(userId, new NotificationRequestDto(null, title, body, data));
    }

    private void notifyUser(Long userId, NotificationRequestDto dto) {
        try {
            fcmService.sendNotificationToAllUserDevice(userId, dto);
        } catch (Exception e) {
            log.warn("공유 문제 알림 발송 실패 - userId: {}", userId, e);
        }
    }
}
