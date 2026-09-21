package com.aisip.OnO.backend.concurrency;

import com.aisip.OnO.backend.mission.entity.MissionMetric;
import com.aisip.OnO.backend.mission.entity.MissionProgress;
import com.aisip.OnO.backend.mission.entity.UserMissionStatus;
import com.aisip.OnO.backend.mission.exception.MissionErrorCase;
import com.aisip.OnO.backend.mission.support.MissionSystemTestSupport;
import com.aisip.OnO.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 같은 사용자의 미션 진행도/보상 요청이 동시에 들어올 때.
 *
 * <p>두 가지가 걸려 있다. 하나는 <b>증가분 유실</b>이다. 진행도를 읽고 계산해서 저장하면
 * 동시에 들어온 요청들이 같은 값을 읽고 같은 값을 써서 8번 복습한 것이 1번으로 기록된다.
 * 다른 하나는 <b>중복 지급</b>이다. "아직 안 받았다"를 조회로 확인하고 지급하면 버튼을 두 번 빠르게
 * 누른 두 요청이 모두 통과한다.
 *
 * <p>방어는 각각 upsert 한 문장({@code INSERT ... ON DUPLICATE KEY UPDATE})과
 * {@code claimed_at IS NULL} 조건부 UPDATE 다. 그 두 가지가 정말 동작하는지 여기서 고정한다.
 */
@DisplayName("동시성 - 미션 진행도와 보상 받기")
class MissionProgressConcurrencyTest extends MissionSystemTestSupport {

    private static final int THREAD_COUNT = 8;

    private User user;

    @BeforeEach
    void setUpUser() {
        user = fixtures.createUser();
    }

    @Nested
    @DisplayName("진행도 증가")
    class Increase {

        @Test
        @DisplayName("복습 8번이 동시에 들어와도 진행도가 어긋나지 않는다")
        void doesNotLoseIncrements() {
            ConcurrentRunner.Outcome outcome = ConcurrentRunner.runConcurrently(
                    THREAD_COUNT, () -> missionProgressUpdater.increase(user.getId(), MissionMetric.SOLVE_RECORDED));

            assertThat(outcome.serverErrors())
                    .as("동시 요청이 교착이나 유니크 제약 위반으로 500 이 되면 안 된다")
                    .isEmpty();
            assertThat(currentOf(user.getId(), WEEKLY_REVIEW_30))
                    .as("8번 복습했으면 8이어야 한다")
                    .isEqualTo(THREAD_COUNT);
            assertThat(currentOf(user.getId(), DAILY_REVIEW_3))
                    .as("목표가 3인 미션은 3에서 멈춘다")
                    .isEqualTo(3);
        }

        @Test
        @DisplayName("동시에 들어와도 진행도 행은 사용자·미션·기간당 하나뿐이다")
        void createsSingleRowPerPeriod() {
            ConcurrentRunner.runConcurrently(
                    THREAD_COUNT, () -> missionProgressUpdater.increase(user.getId(), MissionMetric.MOOD_LOGGED));

            assertThat(missionProgressRepository.findAll())
                    .as("MOOD_LOGGED 를 세는 미션은 일일 하나뿐이다")
                    .hasSize(1);
        }
    }

    @Nested
    @DisplayName("보상 받기")
    class Claim {

        @Test
        @DisplayName("동시에 여덟 번 눌러도 한 번만 지급된다")
        void grantsOnce() {
            MissionProgress progress = completeMission(user.getId(), DAILY_REVIEW_3);
            long before = problemPracticePoints();

            ConcurrentRunner.Outcome outcome = ConcurrentRunner.runConcurrently(
                    THREAD_COUNT, () -> missionService.claim(user.getId(), progress.getId()));

            assertThat(outcome.serverErrors()).isEmpty();
            assertThat(outcome.successCount())
                    .as("받기는 한 번만 성공해야 한다")
                    .isEqualTo(1);
            assertThat(outcome.rejectedErrorCases())
                    .as("나머지는 이미 받은 미션으로 거절된다")
                    .containsOnly(MissionErrorCase.MISSION_ALREADY_CLAIMED);
            assertThat(problemPracticePoints() - before)
                    .as("XP 도 한 번치만 들어와야 한다")
                    .isEqualTo(15);
        }

        private long problemPracticePoints() {
            UserMissionStatus status = reload(user).getUserMissionStatus();
            return accumulatedPoints(status.getProblemPracticeLevel(), status.getProblemPracticePoint());
        }
    }
}
