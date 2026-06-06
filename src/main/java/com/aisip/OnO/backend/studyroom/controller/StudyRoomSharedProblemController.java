package com.aisip.OnO.backend.studyroom.controller;

import com.aisip.OnO.backend.common.response.CommonResponse;
import com.aisip.OnO.backend.common.response.CursorPageResponse;
import com.aisip.OnO.backend.studyroom.dto.StudyRoomDtos.*;
import com.aisip.OnO.backend.studyroom.service.StudyRoomSharedProblemService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/study-room/{roomId}/shared-problems")
public class StudyRoomSharedProblemController {

    private final StudyRoomSharedProblemService sharedProblemService;

    @GetMapping
    public CommonResponse<CursorPageResponse<SharedProblemResponse>> getSharedProblems(@PathVariable Long roomId,
                                                                                       @RequestParam(value = "cursor", required = false) Long cursor,
                                                                                       @RequestParam(defaultValue = "20") int size) {
        return CommonResponse.success(sharedProblemService.getSharedProblems(roomId, currentUserId(), cursor, size));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CommonResponse<SharedProblemResponse> shareProblem(@PathVariable Long roomId,
                                                              @RequestBody SharedProblemCreateRequest request) {
        return CommonResponse.success(sharedProblemService.shareProblem(roomId, currentUserId(), request));
    }

    @PostMapping("/{sharedProblemId}/reactions")
    public CommonResponse<SharedProblemReactionToggleResponse> toggleReaction(@PathVariable Long roomId,
                                                                              @PathVariable Long sharedProblemId,
                                                                              @RequestBody ReactionToggleRequest request) {
        return CommonResponse.success(sharedProblemService.toggleReaction(roomId, sharedProblemId, currentUserId(), request));
    }

    @DeleteMapping("/{sharedProblemId}")
    public CommonResponse<String> deleteSharedProblem(@PathVariable Long roomId, @PathVariable Long sharedProblemId) {
        sharedProblemService.deleteSharedProblem(roomId, sharedProblemId, currentUserId());
        return CommonResponse.success("공유 문제 삭제가 완료되었습니다.");
    }

    private Long currentUserId() {
        return (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
