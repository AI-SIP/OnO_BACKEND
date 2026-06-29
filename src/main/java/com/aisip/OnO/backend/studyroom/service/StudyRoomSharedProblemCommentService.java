package com.aisip.OnO.backend.studyroom.service;

import com.aisip.OnO.backend.common.emoji.CustomEmojiValidator;
import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.common.response.CursorPageResponse;
import com.aisip.OnO.backend.studyroom.dto.StudyRoomDtos.*;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomMember;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomMemberRole;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomSharedProblem;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomSharedProblemComment;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomSharedProblemCommentReaction;
import com.aisip.OnO.backend.studyroom.exception.StudyRoomErrorCase;
import com.aisip.OnO.backend.studyroom.repository.StudyRoomSharedProblemCommentRepository;
import com.aisip.OnO.backend.studyroom.repository.StudyRoomSharedProblemCommentReactionRepository;
import com.aisip.OnO.backend.studyroom.repository.StudyRoomSharedProblemRepository;
import com.aisip.OnO.backend.user.entity.User;
import com.aisip.OnO.backend.user.exception.UserErrorCase;
import com.aisip.OnO.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StudyRoomSharedProblemCommentService {

    private static final int MAX_COMMENT_LENGTH = 300;

    private final StudyRoomAccessService accessService;
    private final StudyRoomSharedProblemRepository sharedProblemRepository;
    private final StudyRoomSharedProblemCommentRepository commentRepository;
    private final StudyRoomSharedProblemCommentReactionRepository commentReactionRepository;
    private final UserRepository userRepository;
    private final StudyRoomReactionService reactionService;
    private final CustomEmojiValidator customEmojiValidator;

    @Transactional(readOnly = true)
    public CursorPageResponse<SharedProblemCommentResponse> getComments(Long roomId, Long sharedProblemId,
                                                                        Long userId, Long cursor, int size) {
        accessService.validateMember(roomId, userId);
        getSharedProblemOrThrow(roomId, sharedProblemId);
        int safeSize = Math.min(Math.max(size, 1), 50);
        List<StudyRoomSharedProblemComment> comments = commentRepository.findBySharedProblemIdAndCursor(
                sharedProblemId, cursor, PageRequest.of(0, safeSize + 1));
        boolean hasNext = comments.size() > safeSize;
        List<StudyRoomSharedProblemComment> contentComments = hasNext ? comments.subList(0, safeSize) : comments;
        List<Long> commentIds = contentComments.stream()
                .map(StudyRoomSharedProblemComment::getId)
                .toList();
        Map<Long, List<StudyRoomSharedProblemCommentReaction>> reactions = commentIds.isEmpty()
                ? Map.of()
                : commentReactionRepository.findAllByCommentIds(commentIds).stream()
                .collect(Collectors.groupingBy(reaction -> reaction.getComment().getId()));
        List<SharedProblemCommentResponse> content = contentComments.stream()
                .map(comment -> toResponse(comment, reactions.getOrDefault(comment.getId(), List.of()), userId))
                .toList();
        Long nextCursor = hasNext && !contentComments.isEmpty()
                ? contentComments.get(contentComments.size() - 1).getId()
                : null;
        return new CursorPageResponse<>(content, nextCursor, hasNext, safeSize);
    }

    @Transactional
    public SharedProblemCommentResponse createComment(Long roomId, Long sharedProblemId, Long userId,
                                                      SharedProblemCommentRequest request) {
        accessService.validateMember(roomId, userId);
        StudyRoomSharedProblem sharedProblem = getSharedProblemOrThrow(roomId, sharedProblemId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApplicationException(UserErrorCase.USER_NOT_FOUND));
        StudyRoomSharedProblemComment comment = commentRepository.save(
                StudyRoomSharedProblemComment.create(sharedProblem, user, validateContent(request)));
        return toResponse(comment, List.of(), userId, false);
    }

    @Transactional
    public SharedProblemCommentResponse updateComment(Long roomId, Long sharedProblemId, Long commentId,
                                                      Long userId, SharedProblemCommentRequest request) {
        accessService.validateMember(roomId, userId);
        StudyRoomSharedProblemComment comment = getCommentOrThrow(roomId, sharedProblemId, commentId);
        if (!comment.getAuthor().getId().equals(userId)) {
            throw new ApplicationException(StudyRoomErrorCase.SHARED_PROBLEM_COMMENT_FORBIDDEN);
        }
        comment.updateContent(validateContent(request));
        return toResponse(comment, commentReactionRepository.findAllByCommentId(commentId), userId, true);
    }

    @Transactional
    public SharedProblemCommentReactionToggleResponse toggleReaction(Long roomId, Long sharedProblemId, Long commentId,
                                                                     Long userId, ReactionToggleRequest request) {
        accessService.validateMember(roomId, userId);
        customEmojiValidator.validate(request.emoji());
        StudyRoomSharedProblemComment comment = getCommentOrThrow(roomId, sharedProblemId, commentId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApplicationException(UserErrorCase.USER_NOT_FOUND));
        commentReactionRepository.findByCommentIdAndUserIdAndEmoji(commentId, userId, request.emoji())
                .ifPresentOrElse(commentReactionRepository::delete,
                        () -> commentReactionRepository.save(StudyRoomSharedProblemCommentReaction.create(comment, user, request.emoji())));
        return new SharedProblemCommentReactionToggleResponse(commentId,
                reactionService.summarizeSharedProblemCommentReactions(commentReactionRepository.findAllByCommentId(commentId), userId));
    }

    @Transactional
    public void deleteComment(Long roomId, Long sharedProblemId, Long commentId, Long userId) {
        StudyRoomMember member = accessService.getMemberOrThrow(roomId, userId);
        StudyRoomSharedProblemComment comment = getCommentOrThrow(roomId, sharedProblemId, commentId);
        boolean mine = comment.getAuthor().getId().equals(userId);
        boolean host = member.getRole() == StudyRoomMemberRole.HOST;
        if (!mine && !host) {
            throw new ApplicationException(StudyRoomErrorCase.SHARED_PROBLEM_COMMENT_FORBIDDEN);
        }
        commentReactionRepository.deleteByCommentId(commentId);
        commentRepository.delete(comment);
    }

    private StudyRoomSharedProblem getSharedProblemOrThrow(Long roomId, Long sharedProblemId) {
        return sharedProblemRepository.findByIdAndRoomId(sharedProblemId, roomId)
                .orElseThrow(() -> new ApplicationException(StudyRoomErrorCase.SHARED_PROBLEM_NOT_FOUND));
    }

    private StudyRoomSharedProblemComment getCommentOrThrow(Long roomId, Long sharedProblemId, Long commentId) {
        return commentRepository.findByIdAndSharedProblemIdAndRoomId(commentId, sharedProblemId, roomId)
                .orElseThrow(() -> new ApplicationException(StudyRoomErrorCase.SHARED_PROBLEM_COMMENT_NOT_FOUND));
    }

    private String validateContent(SharedProblemCommentRequest request) {
        String content = request == null ? null : request.content();
        if (content == null || content.isBlank() || content.length() > MAX_COMMENT_LENGTH) {
            throw new ApplicationException(StudyRoomErrorCase.INVALID_SHARED_PROBLEM_COMMENT);
        }
        return content.trim();
    }

    private SharedProblemCommentResponse toResponse(StudyRoomSharedProblemComment comment, Long userId) {
        return toResponse(comment, List.of(), userId, false);
    }

    private SharedProblemCommentResponse toResponse(StudyRoomSharedProblemComment comment,
                                                    List<StudyRoomSharedProblemCommentReaction> reactions,
                                                    Long userId) {
        return toResponse(comment, reactions, userId, false);
    }

    private SharedProblemCommentResponse toResponse(StudyRoomSharedProblemComment comment,
                                                    List<StudyRoomSharedProblemCommentReaction> reactions,
                                                    Long userId,
                                                    boolean forceEdited) {
        LocalDateTime createdAt = comment.getCreatedAt();
        LocalDateTime updatedAt = comment.getUpdatedAt();
        Long authorId = comment.getAuthor().getId();
        boolean mine = authorId.equals(userId);
        boolean canDelete = mine || comment.getSharedProblem().getRoom().getHostUserId().equals(userId);
        return new SharedProblemCommentResponse(
                comment.getId(),
                comment.getContent(),
                authorId,
                comment.getAuthor().getName(),
                comment.getAuthor().getProfileImageUrl(),
                createdAt,
                updatedAt,
                forceEdited || createdAt != null && updatedAt != null && updatedAt.isAfter(createdAt),
                mine,
                canDelete,
                reactionService.summarizeSharedProblemCommentReactions(reactions, userId)
        );
    }
}
