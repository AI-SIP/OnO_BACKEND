package com.aisip.OnO.backend.mission.service;

import com.aisip.OnO.backend.mission.entity.MissionProgress;
import com.aisip.OnO.backend.mission.entity.MissionType;
import com.aisip.OnO.backend.mission.entity.UserMissionStatus;
import com.aisip.OnO.backend.mission.support.MissionSystemTestSupport;
import com.aisip.OnO.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 자동 적립 플래그({@code ono.mission.legacy-accrual.enabled})의 켜짐/꺼짐 동작.
 *
 * <p>백엔드가 프론트보다 먼저 나가도 지장이 없어야 해서 둔 스위치다. 켜 두면 지금 운영과 똑같이 돌고,
 * 프론트에 미션 화면이 나간 뒤 끄면 흡수가 끝난다. 되돌리려면 다시 켜면 된다.
 *
 * <p>플래그가 <b>무엇을 끄고 무엇을 끄지 않는지</b>가 이 클래스의 주제다.
 * 꺼지는 것은 포인트 지급 하나뿐이고, 기록과 중복 방지와 진행도는 양쪽에서 똑같이 돈다.
 */
@DisplayName("자동 적립 플래그")
class MissionLegacyAccrualTest extends MissionSystemTestSupport {

    private User user;

    @BeforeEach
    void setUpUser() {
        user = fixtures.createUser();
    }

    @Test
    @DisplayName("설정을 건드리지 않으면 켜짐이다")
    void defaultsToEnabled() throws NoSuchFieldException {
        // 기본값이 꺼짐이면 백엔드만 배포하는 순간 XP 유입이 통째로 멈춘다.
        // 필드 값이 아니라 선언을 본다. 테스트가 값을 바꿔 가며 도는데 값으로 확인하면 순환 논증이다.
        Field field = MissionLogService.class.getDeclaredField("legacyAccrualEnabled");

        assertThat(field.getAnnotation(Value.class).value())
                .as("프로퍼티 이름과 기본값은 운영에서 끌 때 쓰는 계약이다")
                .isEqualTo("${ono.mission.legacy-accrual.enabled:true}");
    }

    @Nested
    @DisplayName("켜져 있으면")
    class Enabled {

        @BeforeEach
        void enable() {
            setLegacyAccrual(true);
        }

        @Test
        @DisplayName("행동만으로 경험치가 들어오고 진행도도 함께 오른다")
        void grantsPointAndRaisesProgress() {
            missionLogService.registerLoginMission(user.getId());

            assertThat(accumulatedPoints(status().getAttendanceLevel(), status().getAttendancePoint()))
                    .as("출석 자동 적립")
                    .isEqualTo(MissionType.USER_LOGIN.getPoint());
            assertThat(currentOf(user.getId(), DAILY_ATTEND))
                    .as("진행도는 플래그와 무관하게 오른다")
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("하루 200점 상한이 걸린다")
        void capsDailyGainAtTwoHundred() {
            for (int i = 1; i <= 14; i++) {
                missionLogService.registerNotePracticeMission(user.getId(), (long) i);
            }

            assertThat(accumulatedNotePracticePoints(user))
                    .as("""
                            상한 판정은 오늘 쌓인 mission_log.point 합으로 한다.
                            13번째에 이미 195점이 쌓여 남은 5점만 받고, 14번째는 210점을 넘겨 0점이다.
                            그래서 12*15 + 5 = 185 에서 멈춘다.""")
                    .isEqualTo(185L);
        }

        @Test
        @DisplayName("상한을 채워도 미션 보상은 그대로 들어온다")
        void missionRewardIgnoresDailyCap() {
            for (int i = 1; i <= 14; i++) {
                missionLogService.registerNotePracticeMission(user.getId(), (long) i);
            }
            long before = problemPracticePoints();

            MissionProgress progress = completeMission(user.getId(), DAILY_REVIEW_3);
            missionService.claim(user.getId(), progress.getId());

            assertThat(problemPracticePoints() - before)
                    .as("상한은 자동 적립에만 있는 규칙이다. 미션 보상은 별도 경로다")
                    .isEqualTo(15L);
        }
    }

    @Nested
    @DisplayName("꺼져 있으면")
    class Disabled {

        @BeforeEach
        void disable() {
            setLegacyAccrual(false);
        }

        @Test
        @DisplayName("행동해도 경험치는 오르지 않는다")
        void grantsNoPoint() {
            missionLogService.registerLoginMission(user.getId());

            assertThat(status().getTotalStudyPoint()).isZero();
        }

        @Test
        @DisplayName("경험치를 안 줘도 기록은 그대로 남는다")
        void stillWritesMissionLog() {
            missionLogService.registerLoginMission(user.getId());
            missionLogService.registerNotePracticeMission(user.getId(), 1L);

            assertThat(missionLogRepository.findAllByUserId(user.getId()))
                    .as("관리자 통계가 이 행을 읽는다. 꺼지는 것은 지급뿐이다")
                    .hasSize(2);
        }

        @Test
        @DisplayName("진행도는 그대로 오르고 중복 방지도 그대로 동작한다")
        void stillRaisesProgressAndGuardsDuplicates() {
            missionLogService.registerLoginMission(user.getId());
            missionLogService.registerLoginMission(user.getId());

            assertThat(currentOf(user.getId(), DAILY_ATTEND)).isEqualTo(1);
            assertThat(missionLogRepository.findAllByUserId(user.getId()))
                    .as("하루에 한 번만 기록된다")
                    .hasSize(1);
        }

        @Test
        @DisplayName("미션을 받아야만 경험치가 오른다")
        void onlyMissionClaimGrantsPoint() {
            MissionProgress progress = completeMission(user.getId(), DAILY_REVIEW_3);
            assertThat(problemPracticePoints()).isZero();

            missionService.claim(user.getId(), progress.getId());

            assertThat(problemPracticePoints()).isEqualTo(15L);
        }
    }

    private UserMissionStatus status() {
        return reload(user).getUserMissionStatus();
    }

    private long problemPracticePoints() {
        return accumulatedPoints(status().getProblemPracticeLevel(), status().getProblemPracticePoint());
    }
}
