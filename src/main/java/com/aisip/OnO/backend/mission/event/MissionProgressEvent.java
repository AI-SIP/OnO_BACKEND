package com.aisip.OnO.backend.mission.event;

import com.aisip.OnO.backend.mission.entity.MissionMetric;

/**
 * "사용자가 세는 항목에 해당하는 행동을 했다"는 사실.
 *
 * <p>행동을 처리하는 트랜잭션이 커밋된 뒤에 소비된다. 커밋 전에는 아직 일어나지 않은 일이므로
 * 진행도를 올리면 안 되고, 커밋 뒤라면 진행도가 실패하더라도 행동은 이미 끝난 일이라 되돌리면 안 된다.
 */
public record MissionProgressEvent(
        Long userId,
        MissionMetric metric,
        int amount
) {
}
