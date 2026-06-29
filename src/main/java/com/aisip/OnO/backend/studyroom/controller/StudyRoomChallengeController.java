package com.aisip.OnO.backend.studyroom.controller;

import com.aisip.OnO.backend.common.response.CommonResponse;
import com.aisip.OnO.backend.studyroom.dto.StudyRoomDtos.*;
import com.aisip.OnO.backend.studyroom.service.StudyRoomChallengeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/study-room/{roomId}/challenges")
public class StudyRoomChallengeController {

    private final StudyRoomChallengeService challengeService;

    @GetMapping
    public CommonResponse<List<ChallengeResponse>> getChallenges(@PathVariable("roomId") Long roomId) {
        return CommonResponse.success(challengeService.getChallenges(roomId, currentUserId()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CommonResponse<ChallengeResponse> createChallenge(@PathVariable("roomId") Long roomId,
                                                             @RequestBody ChallengeCreateRequest request) {
        return CommonResponse.success(challengeService.createChallenge(roomId, currentUserId(), request));
    }

    @DeleteMapping("/{challengeId}")
    public CommonResponse<String> deleteChallenge(@PathVariable("roomId") Long roomId,
                                                  @PathVariable("challengeId") Long challengeId) {
        challengeService.deleteChallenge(roomId, challengeId, currentUserId());
        return CommonResponse.success("챌린지 삭제가 완료되었습니다.");
    }

    private Long currentUserId() {
        return (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
