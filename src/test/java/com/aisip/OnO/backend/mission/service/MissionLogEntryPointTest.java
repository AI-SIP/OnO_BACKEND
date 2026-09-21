package com.aisip.OnO.backend.mission.service;

import com.aisip.OnO.backend.mission.support.MissionSystemTestSupport;
import com.aisip.OnO.backend.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 미션 적립 진입점이 하나도 빠짐없이 진행도를 함께 올리는지.
 *
 * <p>{@code registerMissionLog} 라는 범용 진입점이 있었다. 로그는 저장하는데 진행도는 올리지 않아,
 * 누가 그걸 쓰는 순간 조용히 깨지는 구조였다. 로그가 먼저 생겨 {@code alreadyXxx} 가 참이 되면
 * 뒤이어 전용 메서드를 불러도 중복 방지에 걸려 진행도는 영영 오르지 않는다.
 * 호출자가 없어 지웠고, 같은 모양이 다시 생기지 않도록 여기서 막는다.
 */
@DisplayName("미션 적립 진입점")
class MissionLogEntryPointTest extends MissionSystemTestSupport {

    @Test
    @DisplayName("진행도를 올리지 않는 범용 등록 진입점이 없다")
    void hasNoGenericRegisterEntryPoint() {
        boolean exists = Arrays.stream(MissionLogService.class.getDeclaredMethods())
                .map(Method::getName)
                .anyMatch("registerMissionLog"::equals);

        assertThat(exists)
                .as("범용 진입점을 되살리려면 중복 방지 판정 안에서 진행도도 함께 올려야 한다")
                .isFalse();
    }

    @Test
    @DisplayName("MissionLogService 가 직접 적립하는 진입점은 진행도도 함께 올린다")
    void everyRegisterEntryPointRaisesProgress() {
        User user = fixtures.createUser();

        missionLogService.registerLoginMission(user.getId());
        assertThat(currentOf(user.getId(), DAILY_ATTEND)).as("출석").isEqualTo(1);
        assertThat(currentOf(user.getId(), WEEKLY_ATTEND_5)).as("주간 출석").isEqualTo(1);

        missionLogService.registerNotePracticeMission(user.getId(), 777L);
        assertThat(currentOf(user.getId(), DAILY_PRACTICE_SET)).as("복습 세트 완료").isEqualTo(1);
        assertThat(currentOf(user.getId(), WEEKLY_SET_3)).as("주간 세트").isEqualTo(1);

        // 오답노트 등록(PROBLEM_CREATED)과 복습 기록(SOLVE_RECORDED)은 MissionLogService 가 아니라
        // 각 도메인 서비스에서 올린다. 그쪽은 MissionProgressHookTest 가 실제 경로로 확인한다.
    }
}
