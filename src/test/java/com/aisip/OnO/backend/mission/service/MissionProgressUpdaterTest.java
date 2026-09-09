package com.aisip.OnO.backend.mission.service;

import com.aisip.OnO.backend.mission.entity.MissionDefinition;
import com.aisip.OnO.backend.mission.entity.MissionMetric;
import com.aisip.OnO.backend.mission.entity.MissionProgress;
import com.aisip.OnO.backend.mission.support.MissionSystemTestSupport;
import com.aisip.OnO.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("미션 진행도 증가")
class MissionProgressUpdaterTest extends MissionSystemTestSupport {

    private User user;

    @BeforeEach
    void setUpUser() {
        user = fixtures.createUser();
    }

    @Test
    @DisplayName("행동 한 번이면 진행도가 1 오른다")
    void increasesByOne() {
        missionProgressUpdater.increase(user.getId(), MissionMetric.PROBLEM_CREATED);

        assertThat(currentOf(user.getId(), DAILY_NOTE_WRITE)).isEqualTo(1);
    }

    @Test
    @DisplayName("한 번의 행동이 같은 항목을 세는 일일과 주간을 함께 올린다")
    void increasesDailyAndWeeklyTogether() {
        missionProgressUpdater.increase(user.getId(), MissionMetric.PROBLEM_CREATED);

        assertThat(currentOf(user.getId(), DAILY_NOTE_WRITE)).isEqualTo(1);
        assertThat(currentOf(user.getId(), WEEKLY_NOTE_10)).isEqualTo(1);
    }

    @Test
    @DisplayName("여러 장을 한 번에 등록하면 장수만큼 오른다")
    void increasesByGivenAmount() {
        missionProgressUpdater.increase(user.getId(), MissionMetric.PROBLEM_CREATED, 3);

        assertThat(currentOf(user.getId(), WEEKLY_NOTE_10)).isEqualTo(3);
    }

    @Test
    @DisplayName("목표에 닿으면 완료 시각이 찍힌다")
    void marksCompletedWhenTargetReached() {
        missionProgressUpdater.increase(user.getId(), MissionMetric.SOLVE_RECORDED, 3);

        MissionProgress progress = progressOf(user, DAILY_REVIEW_3);
        assertThat(progress.getCurrentValue()).isEqualTo(3);
        assertThat(progress.getCompletedAt()).as("목표를 채웠으면 완료 시각이 있어야 한다").isNotNull();
    }

    @Test
    @DisplayName("목표에 닿기 전에는 완료가 아니다")
    void doesNotCompleteBeforeTarget() {
        missionProgressUpdater.increase(user.getId(), MissionMetric.SOLVE_RECORDED, 2);

        MissionProgress progress = progressOf(user, DAILY_REVIEW_3);
        assertThat(progress.getCurrentValue()).isEqualTo(2);
        assertThat(progress.getCompletedAt()).isNull();
    }

    @Test
    @DisplayName("목표를 넘겨도 진행도가 목표를 넘지 않는다")
    void stopsAtTarget() {
        for (int i = 0; i < 4; i++) {
            missionProgressUpdater.increase(user.getId(), MissionMetric.SOLVE_RECORDED);
        }

        assertThat(currentOf(user.getId(), DAILY_REVIEW_3))
                .as("4번 복습해도 화면에 4 / 3 이 보이면 안 된다")
                .isEqualTo(3);
    }

    @Test
    @DisplayName("목표를 넘겨도 완료 시각은 처음 한 번만 찍힌다")
    void keepsFirstCompletedAt() {
        missionProgressUpdater.increase(user.getId(), MissionMetric.SOLVE_RECORDED, 3);
        var completedAt = progressOf(user, DAILY_REVIEW_3).getCompletedAt();

        missionProgressUpdater.increase(user.getId(), MissionMetric.SOLVE_RECORDED, 3);

        assertThat(progressOf(user, DAILY_REVIEW_3).getCompletedAt()).isEqualTo(completedAt);
    }

    @Nested
    @DisplayName("기간이 다르면 별개다")
    class PeriodSeparation {

        @Test
        @DisplayName("어제 진행도가 오늘에 섞이지 않는다")
        void yesterdayDoesNotLeakIntoToday() {
            MissionDefinition definition = definitionOf(DAILY_NOTE_WRITE);
            String yesterdayKey = MissionPeriodKey.daily(MissionPeriodKey.today().minusDays(1));
            // 리셋 배치가 없으므로 어제 행은 그대로 남아 있다. 오늘 키로 조회할 때 잡히지 않는 것이 전부다.
            jdbcTemplate.update("""
                    INSERT INTO mission_progress
                        (user_id, mission_id, period_key, current_value, target_snapshot, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, NOW(6), NOW(6))
                    """, user.getId(), definition.getId(), yesterdayKey, 1, definition.getTarget());

            missionProgressUpdater.increase(user.getId(), MissionMetric.PROBLEM_CREATED);

            assertThat(currentOf(user.getId(), DAILY_NOTE_WRITE))
                    .as("오늘 진행도는 어제와 무관하게 1 이다")
                    .isEqualTo(1);
            assertThat(missionProgressRepository.findAll())
                    .as("어제 행과 오늘 행은 서로 다른 행이다")
                    .filteredOn(progress -> progress.getMissionId().equals(definition.getId()))
                    .hasSize(2);
        }

        @Test
        @DisplayName("지난 주 진행도가 이번 주에 섞이지 않는다")
        void lastWeekDoesNotLeakIntoThisWeek() {
            MissionDefinition definition = definitionOf(WEEKLY_NOTE_10);
            LocalDate lastWeek = MissionPeriodKey.today().minusWeeks(1);
            jdbcTemplate.update("""
                    INSERT INTO mission_progress
                        (user_id, mission_id, period_key, current_value, target_snapshot, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, NOW(6), NOW(6))
                    """, user.getId(), definition.getId(), MissionPeriodKey.weekly(lastWeek), 9, definition.getTarget());

            missionProgressUpdater.increase(user.getId(), MissionMetric.PROBLEM_CREATED);

            assertThat(currentOf(user.getId(), WEEKLY_NOTE_10)).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("트랜잭션 경계")
    class TransactionBoundary {

        @Test
        @DisplayName("행동이 롤백되면 진행도도 남지 않는다")
        void rollsBackWithTheAction() {
            // 진행도를 별도 트랜잭션(REQUIRES_NEW)이나 이벤트 리스너로 떼어내면 이 단언이 깨진다.
            // 하지도 않은 행동으로 보상을 받게 되므로 호출자의 트랜잭션 안에서 돌아야 한다.
            assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
                missionProgressUpdater.increase(user.getId(), MissionMetric.PROBLEM_CREATED);
                throw new IllegalStateException("행동이 실패했다");
            })).isInstanceOf(IllegalStateException.class);

            assertThat(missionProgressRepository.findAll()).isEmpty();
        }
    }

    @Nested
    @DisplayName("올리지 않는 경우")
    class NoOp {

        @Test
        @DisplayName("증가량이 0 이하면 아무 일도 없다")
        void ignoresNonPositiveAmount() {
            missionProgressUpdater.increase(user.getId(), MissionMetric.PROBLEM_CREATED, 0);

            assertThat(missionProgressRepository.findAll()).isEmpty();
        }

        @Test
        @DisplayName("사용자나 항목이 없으면 아무 일도 없다")
        void ignoresNullArguments() {
            missionProgressUpdater.increase(null, MissionMetric.PROBLEM_CREATED, 1);
            missionProgressUpdater.increase(user.getId(), null, 1);

            assertThat(missionProgressRepository.findAll()).isEmpty();
        }
    }
}
