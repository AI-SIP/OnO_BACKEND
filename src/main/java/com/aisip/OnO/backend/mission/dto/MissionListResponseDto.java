package com.aisip.OnO.backend.mission.dto;

import java.util.List;

/**
 * 미션 목록 응답. 일일과 주간을 각자의 기간 키와 함께 나눠 내려준다.
 *
 * <p>{@code expired} 는 지난 기간에 완료했지만 아직 받지 않은 보상이다. 이 묶음이 없으면
 * 일요일 밤에 주간 미션을 끝내고 받지 않은 채 앱을 닫은 사용자는 월요일부터 그 미션이 목록에서 사라져
 * 보상을 영영 받을 방법이 없다. 받기는 {@code progressId} 로 하므로 기간이 지나도 그대로 동작한다.
 */
public record MissionListResponseDto(
        MissionSectionDto daily,
        MissionSectionDto weekly,
        MissionSectionDto expired
) {

    /**
     * @param periodKey 그 묶음 전체의 기간 키. 여러 기간이 섞이는 {@code expired} 에서는 null 이고,
     *                  기간은 항목마다 실린 {@code periodKey} 로 확인한다.
     */
    public record MissionSectionDto(
            String periodKey,
            List<MissionResponseDto> missions
    ) {
    }
}
