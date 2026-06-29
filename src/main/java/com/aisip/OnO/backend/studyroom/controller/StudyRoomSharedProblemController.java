package com.aisip.OnO.backend.studyroom.controller;

import com.aisip.OnO.backend.common.response.CommonResponse;
import com.aisip.OnO.backend.common.response.CursorPageResponse;
import com.aisip.OnO.backend.studyroom.dto.StudyRoomDtos.*;
import com.aisip.OnO.backend.studyroom.service.StudyRoomSharedProblemCommentService;
import com.aisip.OnO.backend.studyroom.service.StudyRoomSharedProblemService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping({"/api/study-room/{roomId}/shared-problems", "/api/study-rooms/{roomId}/shared-problems"})
public class StudyRoomSharedProblemController {

    private final StudyRoomSharedProblemService sharedProblemService;
    private final StudyRoomSharedProblemCommentService commentService;

    @GetMapping
    public CommonResponse<CursorPageResponse<SharedProblemResponse>> getSharedProblems(@PathVariable("roomId") Long roomId,
                                                                                       @RequestParam(value = "cursor", required = false) Long cursor,
                                                                                       @RequestParam(value = "size", defaultValue = "20") int size) {
        return CommonResponse.success(sharedProblemService.getSharedProblems(roomId, currentUserId(), cursor, size));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CommonResponse<SharedProblemResponse> shareProblem(@PathVariable("roomId") Long roomId,
                                                              @RequestBody SharedProblemCreateRequest request) {
        return CommonResponse.success(sharedProblemService.shareProblem(roomId, currentUserId(), request));
    }

    @PostMapping("/{sharedProblemId}/reactions")
    public CommonResponse<SharedProblemReactionToggleResponse> toggleReaction(@PathVariable("roomId") Long roomId,
                                                                              @PathVariable("sharedProblemId") Long sharedProblemId,
                                                                              @RequestBody ReactionToggleRequest request) {
        return CommonResponse.success(sharedProblemService.toggleReaction(roomId, sharedProblemId, currentUserId(), request));
    }

    @GetMapping("/{sharedProblemId}/comments")
    public CommonResponse<CursorPageResponse<SharedProblemCommentResponse>> getComments(@PathVariable("roomId") Long roomId,
                                                                                        @PathVariable("sharedProblemId") Long sharedProblemId,
                                                                                        @RequestParam(value = "cursor", required = false) Long cursor,
                                                                                        @RequestParam(value = "size", defaultValue = "20") int size) {
        return CommonResponse.success(commentService.getComments(roomId, sharedProblemId, currentUserId(), cursor, size));
    }

    @PostMapping("/{sharedProblemId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommonResponse<SharedProblemCommentResponse> createComment(@PathVariable("roomId") Long roomId,
                                                                      @PathVariable("sharedProblemId") Long sharedProblemId,
                                                                      @RequestBody SharedProblemCommentRequest request) {
        return CommonResponse.success(commentService.createComment(roomId, sharedProblemId, currentUserId(), request));
    }

    @PatchMapping("/{sharedProblemId}/comments/{commentId}")
    public CommonResponse<SharedProblemCommentResponse> updateComment(@PathVariable("roomId") Long roomId,
                                                                      @PathVariable("sharedProblemId") Long sharedProblemId,
                                                                      @PathVariable("commentId") Long commentId,
                                                                      @RequestBody SharedProblemCommentRequest request) {
        return CommonResponse.success(commentService.updateComment(roomId, sharedProblemId, commentId, currentUserId(), request));
    }

    @PostMapping("/{sharedProblemId}/comments/{commentId}/reactions")
    public CommonResponse<SharedProblemCommentReactionToggleResponse> toggleCommentReaction(@PathVariable("roomId") Long roomId,
                                                                                            @PathVariable("sharedProblemId") Long sharedProblemId,
                                                                                            @PathVariable("commentId") Long commentId,
                                                                                            @RequestBody ReactionToggleRequest request) {
        return CommonResponse.success(commentService.toggleReaction(roomId, sharedProblemId, commentId, currentUserId(), request));
    }

    @DeleteMapping("/{sharedProblemId}/comments/{commentId}")
    public CommonResponse<String> deleteComment(@PathVariable("roomId") Long roomId,
                                                @PathVariable("sharedProblemId") Long sharedProblemId,
                                                @PathVariable("commentId") Long commentId) {
        commentService.deleteComment(roomId, sharedProblemId, commentId, currentUserId());
        return CommonResponse.success("공유 문제 댓글 삭제가 완료되었습니다.");
    }

    @DeleteMapping("/{sharedProblemId}")
    public CommonResponse<String> deleteSharedProblem(@PathVariable("roomId") Long roomId,
                                                      @PathVariable("sharedProblemId") Long sharedProblemId) {
        sharedProblemService.deleteSharedProblem(roomId, sharedProblemId, currentUserId());
        return CommonResponse.success("공유 문제 삭제가 완료되었습니다.");
    }

    private Long currentUserId() {
        return (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
