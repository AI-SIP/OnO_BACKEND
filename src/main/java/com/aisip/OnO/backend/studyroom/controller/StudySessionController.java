package com.aisip.OnO.backend.studyroom.controller;

import com.aisip.OnO.backend.common.response.CommonResponse;
import com.aisip.OnO.backend.studyroom.dto.StudyRoomDtos.*;
import com.aisip.OnO.backend.studyroom.service.StudySessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/study-room/{roomId}/sessions")
public class StudySessionController {

    private final StudySessionService sessionService;

    @GetMapping("/active")
    public CommonResponse<ActiveStudySessionsResponse> getActiveSessions(@PathVariable Long roomId) {
        return CommonResponse.success(sessionService.getActiveSessions(roomId, currentUserId()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CommonResponse<StudySessionStartResponse> start(@PathVariable Long roomId) {
        return CommonResponse.success(sessionService.start(roomId, currentUserId()));
    }

    @PatchMapping("/{sessionId}/end")
    public CommonResponse<StudySessionEndResponse> end(@PathVariable Long roomId, @PathVariable Long sessionId) {
        return CommonResponse.success(sessionService.end(roomId, sessionId, currentUserId()));
    }

    private Long currentUserId() {
        return (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
