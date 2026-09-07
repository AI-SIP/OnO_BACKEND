package com.aisip.OnO.backend.concurrency;

import com.aisip.OnO.backend.mission.entity.MissionType;
import com.aisip.OnO.backend.mission.service.MissionLogService;
import com.aisip.OnO.backend.mission.support.MissionTestSupport;
import com.aisip.OnO.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 같은 사용자의 미션 적립 요청이 동시에 들어올 때.
 *
 * <p>미션 적립은 "오늘 이미 받았는가"를 확인하고 없으면 기록을 남기는 check-then-act 인데,
 * mission_log 에는 중복을 막는 유니크 제약이 없다. 방어는 {@code MissionLogService} 가
 * 사용자 행을 배타 잠금으로 잡아 같은 사용자의 요청을 직렬화하는 것뿐이므로,
 * 그 잠금이 정말 중복 적립과 교착을 모두 막는지 여기서 고정한다.
 *
 * <p>잠금이 없던 시절에는 두 가지가 동시에 터졌다. 적립이 8번 중복됐고,
 * mission_log INSERT 가 잡는 user 공유 잠금과 포인트 UPDATE 가 필요로 하는 배타 잠금이 충돌해
 * {@code Deadlock found when trying to get lock} 이 500 으로 새어 나갔다.
 *
 * <p><b>남은 구멍</b>: 미션 적립이 호출자의 트랜잭션 안에서 실행되는 경로
 * (문제 등록·복습 완료 등)는 여기서 다루지 않는다. 그 경우 REPEATABLE READ 스냅샷이
 * 호출자 트랜잭션 시작 시점에 이미 고정돼 있어, 사용자 행을 잠근 뒤에 세는 중복 검사도
 * 앞선 트랜잭션이 커밋한 기록을 보지 못한다. 유니크 제약을 추가하지 않는 한 막을 수 없어
 * 이 테스트의 범위 밖이다.
 */
@DisplayName("동시성 - 미션 보상 중복 적립")
class MissionRewardConcurrencyTest extends MissionTestSupport {

    private static final int THREAD_COUNT = 8;

    @Autowired
    private MissionLogService missionLogService;

    private User user;

    @BeforeEach
    void setUpUser() {
        user = fixtures.createUser();
    }

    @Nested
    @DisplayName("하루 한 번만 주는 미션")
    class OncePerDayMission {

        @Test
        @DisplayName("로그인 미션을 8번 동시에 요청해도 적립은 한 번뿐이다")
        void loginMissionIsGrantedOnce() {
            ConcurrentRunner.Outcome outcome = ConcurrentRunner.runConcurrently(
                    THREAD_COUNT, () -> missionLogService.registerLoginMission(user.getId()));

            assertThat(outcome.serverErrors()).as("중복 요청이 500 으로 나가면 안 된다").isEmpty();
            assertThat(countLogs(MissionType.USER_LOGIN))
                    .as("출석 기록은 하루 한 건")
                    .isEqualTo(1);
            assertThat(attendancePoints())
                    .as("출석 경험치도 한 번치만 들어와야 한다")
                    .isEqualTo(MissionType.USER_LOGIN.getPoint());
        }

        @Test
        @DisplayName("같은 문제의 복습 미션을 8번 동시에 요청해도 적립은 한 번뿐이다")
        void problemPracticeMissionIsGrantedOncePerProblem() {
            long problemId = 4242L;

            ConcurrentRunner.Outcome outcome = ConcurrentRunner.runConcurrently(
                    THREAD_COUNT, () -> missionLogService.registerProblemPracticeMission(user.getId(), problemId));

            assertThat(outcome.serverErrors()).isEmpty();
            assertThat(countLogs(MissionType.PROBLEM_PRACTICE))
                    .as("같은 문제는 하루 한 번만 적립된다")
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("같은 복습노트의 미션을 8번 동시에 요청해도 적립은 한 번뿐이다")
        void notePracticeMissionIsGrantedOncePerNote() {
            long practiceNoteId = 777L;

            ConcurrentRunner.Outcome outcome = ConcurrentRunner.runConcurrently(
                    THREAD_COUNT, () -> missionLogService.registerNotePracticeMission(user.getId(), practiceNoteId));

            assertThat(outcome.serverErrors()).isEmpty();
            assertThat(countLogs(MissionType.NOTE_PRACTICE))
                    .as("같은 복습노트는 하루 한 번만 적립된다")
                    .isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("하루 상한이 있는 미션")
    class DailyCappedMission {

        @Test
        @DisplayName("문제 등록 미션을 8번 동시에 요청해도 하루 3건을 넘지 않는다")
        void problemWriteMissionStopsAtDailyCap() {
            ConcurrentRunner.Outcome outcome = ConcurrentRunner.runConcurrently(
                    THREAD_COUNT, () -> missionLogService.registerProblemWriteMission(user.getId()));

            assertThat(outcome.serverErrors()).isEmpty();
            assertThat(countLogs(MissionType.PROBLEM_WRITE))
                    .as("하루 상한 3건까지만 적립된다")
                    .isEqualTo(3);
            assertThat(noteWritePoints())
                    .as("경험치도 3건치까지만")
                    .isEqualTo(3 * MissionType.PROBLEM_WRITE.getPoint());
        }

    }

    private long countLogs(MissionType missionType) {
        return missionLogRepository.findAllByUserId(user.getId()).stream()
                .filter(log -> log.getMissionType() == missionType)
                .count();
    }

    private long noteWritePoints() {
        return accumulatedPoints(
                reload(user).getUserMissionStatus().getNoteWriteLevel(),
                reload(user).getUserMissionStatus().getNoteWritePoint());
    }

    private long attendancePoints() {
        return accumulatedPoints(
                reload(user).getUserMissionStatus().getAttendanceLevel(),
                reload(user).getUserMissionStatus().getAttendancePoint());
    }
}
