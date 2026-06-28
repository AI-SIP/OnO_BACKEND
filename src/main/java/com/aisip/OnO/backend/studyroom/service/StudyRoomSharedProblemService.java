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
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StudyRoomSharedProblemService {

    private final StudyRoomAccessService accessService;
    private final StudyRoomSharedProblemRepository sharedProblemRepository;
    private final StudyRoomSharedProblemReactionRepository reactionRepository;
    private final StudyRoomSharedProblemCommentRepository commentRepository;
    private final UserRepository userRepository;
    private final ProblemService problemService;
    private final StudyRoomReactionService reactionService;
    private final ApplicationEventPublisher eventPublisher;
    private final CustomEmojiValidator customEmojiValidator;

    @Transactional(readOnly = true)
    public CursorPageResponse<SharedProblemResponse> getSharedProblems(Long roomId, Long userId, Long cursor, int size) {
        accessService.validateMember(roomId, userId);
        int safeSize = Math.min(size, 50);
        List<StudyRoomSharedProblem> sharedProblems = sharedProblemRepository.findByRoomIdAndCursor(roomId, cursor, PageRequest.of(0, safeSize + 1));
        boolean hasNext = sharedProblems.size() > safeSize;
        List<StudyRoomSharedProblem> contentSharedProblems = hasNext ? sharedProblems.subList(0, safeSize) : sharedProblems;
        Map<Long, List<StudyRoomSharedProblemReaction>> reactions = reactionRepository.findAllBySharedProblemIds(
                        contentSharedProblems.stream().map(StudyRoomSharedProblem::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(reaction -> reaction.getSharedProblem().getId()));
        List<SharedProblemResponse> content = contentSharedProblems.stream()
                .map(sharedProblem -> toResponse(sharedProblem, reactions.getOrDefault(sharedProblem.getId(), List.of()), userId))
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
        Problem problem = problemService.findProblemEntityWithImageData(request.problemId(), userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApplicationException(UserErrorCase.USER_NOT_FOUND));
        StudyRoomSharedProblem sharedProblem = sharedProblemRepository.save(
                StudyRoomSharedProblem.create(accessService.getRoomOrThrow(roomId), user, problem, request.comment()));
        eventPublisher.publishEvent(new StudyRoomActivityEvent(userId, StudyRoomFeedEventType.PROBLEM_SHARED,
                Map.of("reference", problem.getReference() == null ? "" : problem.getReference(),
                        "sharedProblemId", sharedProblem.getId())));
        return toResponse(sharedProblem, List.of(), userId);
    }

    @Transactional
    public SharedProblemReactionToggleResponse toggleReaction(Long roomId, Long sharedProblemId, Long userId, ReactionToggleRequest request) {
        accessService.validateMember(roomId, userId);
        customEmojiValidator.validate(request.emoji());
        StudyRoomSharedProblem sharedProblem = sharedProblemRepository.findByIdAndRoomId(sharedProblemId, roomId)
                .orElseThrow(() -> new ApplicationException(StudyRoomErrorCase.SHARED_PROBLEM_NOT_FOUND));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApplicationException(UserErrorCase.USER_NOT_FOUND));
        reactionRepository.findBySharedProblemIdAndUserIdAndEmoji(sharedProblemId, userId, request.emoji())
                .ifPresentOrElse(reactionRepository::delete,
                        () -> reactionRepository.save(StudyRoomSharedProblemReaction.create(sharedProblem, user, request.emoji())));
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
        commentRepository.deleteBySharedProblemId(sharedProblemId);
        reactionRepository.deleteBySharedProblemId(sharedProblemId);
        sharedProblemRepository.delete(sharedProblem);
    }

    private SharedProblemResponse toResponse(StudyRoomSharedProblem sharedProblem,
                                             List<StudyRoomSharedProblemReaction> reactions,
                                             Long userId) {
        Problem problem = sharedProblem.getProblem();
        return new SharedProblemResponse(
                sharedProblem.getId(),
                sharedProblem.getSharedByUser().getId(),
                sharedProblem.getSharedByUser().getName(),
                problem.getId(),
                problemImageUrl(problem),
                problem.getReference(),
                sharedProblem.getComment(),
                sharedProblem.getCreatedAt(),
                reactionService.summarizeSharedProblemReactions(reactions, userId)
        );
    }

    private String problemImageUrl(Problem problem) {
        return problem.getProblemImageDataList().stream()
                .filter(image -> image.getProblemImageType() == ProblemImageType.PROBLEM_IMAGE)
                .map(ProblemImageData::getImageUrl)
                .findFirst()
                .orElse(null);
    }
}
