package com.aisip.OnO.backend.mission.dto;

import java.util.List;

/**
 * 미션 목록 응답. 일일과 주간을 각자의 기간 키와 함께 나눠 내려준다.
 */
public record MissionListResponseDto(
        MissionSectionDto daily,
        MissionSectionDto weekly
) {

    public record MissionSectionDto(
            String periodKey,
            List<MissionResponseDto> missions
    ) {
    }
}
