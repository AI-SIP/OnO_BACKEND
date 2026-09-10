package com.aisip.OnO.backend.mission.controller;

import com.aisip.OnO.backend.common.response.CommonResponse;
import com.aisip.OnO.backend.mission.dto.MissionClaimHistoryResponseDto;
import com.aisip.OnO.backend.mission.dto.MissionClaimResponseDto;
import com.aisip.OnO.backend.mission.dto.MissionListResponseDto;
import com.aisip.OnO.backend.mission.service.MissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/missions")
public class MissionController {

    private final MissionService missionService;

    @GetMapping("")
    public CommonResponse<MissionListResponseDto> getMissions() {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return CommonResponse.success(missionService.getMissions(userId));
    }

    /**
     * 지금까지 받은 보상 기록. 합계는 첫 페이지(커서 없음)에만 실린다.
     */
    @GetMapping("/history")
    public CommonResponse<MissionClaimHistoryResponseDto> getClaimHistory(
            @RequestParam(value = "cursor", required = false) Long cursor,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return CommonResponse.success(missionService.getClaimHistory(userId, cursor, size));
    }

    @PostMapping("/{progressId}/claim")
    public CommonResponse<MissionClaimResponseDto> claim(@PathVariable Long progressId) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return CommonResponse.success(missionService.claim(userId, progressId));
    }
}
