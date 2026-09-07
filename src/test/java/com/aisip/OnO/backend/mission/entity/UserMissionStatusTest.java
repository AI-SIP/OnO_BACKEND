package com.aisip.OnO.backend.mission.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 레벨·경험치 전이 규칙의 단위 테스트.
 *
 * <p>공식이 두 개다.
 * <ul>
 *   <li>개별 능력치: 레벨 n → n+1 에 {@code 10 + (n-1) * 10} 점 필요, 상한 없음</li>
 *   <li>총 학습 레벨: 개별 필요치의 4배, <b>레벨 15에서 멈춤</b></li>
 * </ul>
 * 사용자에게 보이는 숫자라 한 칸만 어긋나도 바로 문의가 들어온다.
 */
@DisplayName("UserMissionStatus")
class UserMissionStatusTest {

    /** 신규 가입자와 같은 상태(모든 능력치 레벨 1, 포인트 0). */
    private UserMissionStatus newcomer() {
        return new UserMissionStatus(1L, 0L, 1L, 0L, 1L, 0L, 1L, 0L, 1L, 0L);
    }

    @Nested
    @DisplayName("개별 능력치 레벨업")
    class AbilityLevelUp {

        @ParameterizedTest(name = "{0}점을 받으면 레벨 {1}, 잔여 {2}점")
        @CsvSource({
                "  0,  1,   0",
                "  9,  1,   9",
                " 10,  2,   0",
                " 11,  2,   1",
                " 15,  2,   5",
                " 29,  2,  19",
                " 30,  3,   0",
                " 60,  4,   0",
                "100,  5,   0"
        })
        @DisplayName("누적 경험치에 따라 레벨과 잔여 포인트가 정해진다")
        void levelsUpByThreshold(long gained, long expectedLevel, long expectedPoint) {
            UserMissionStatus status = newcomer();

            status.gainAttendancePoint(gained);

            assertThat(status.getAttendanceLevel()).isEqualTo(expectedLevel);
            assertThat(status.getAttendancePoint()).isEqualTo(expectedPoint);
        }

        @Test
        @DisplayName("한 번에 여러 레벨을 올릴 수 있다")
        void levelsUpMultipleTimesInOneGain() {
            UserMissionStatus status = newcomer();

            status.gainAttendancePoint(30L);

            assertThat(status.getAttendanceLevel())
                    .as("10점(1→2) + 20점(2→3) = 30점이면 두 단계 올라야 한다")
                    .isEqualTo(3L);
            assertThat(status.getAttendancePoint()).isZero();
        }

        @Test
        @DisplayName("필요 경험치와 정확히 같은 점수면 레벨이 오른다")
        void levelsUpOnExactThreshold() {
            UserMissionStatus status = newcomer();

            status.gainAttendancePoint(9L);
            assertThat(status.getAttendanceLevel()).as("1점 모자라면 오르지 않는다").isEqualTo(1L);

            status.gainAttendancePoint(1L);
            assertThat(status.getAttendanceLevel()).isEqualTo(2L);
            assertThat(status.getAttendancePoint()).isZero();
        }

        @Test
        @DisplayName("0점을 받아도 레벨과 포인트는 그대로다")
        void gainingZeroChangesNothing() {
            UserMissionStatus status = newcomer();
            status.gainAttendancePoint(7L);

            status.gainAttendancePoint(0L);

            assertThat(status.getAttendanceLevel()).isEqualTo(1L);
            assertThat(status.getAttendancePoint()).isEqualTo(7L);
            assertThat(status.getTotalStudyPoint()).isEqualTo(7L);
        }

        @Test
        @DisplayName("능력치는 서로 독립이다 - 출석 경험치가 다른 능력치를 올리지 않는다")
        void abilitiesAreIndependent() {
            UserMissionStatus status = newcomer();

            status.gainAttendancePoint(50L);

            assertThat(status.getAttendanceLevel()).isGreaterThan(1L);
            assertThat(status.getNoteWriteLevel()).isEqualTo(1L);
            assertThat(status.getNoteWritePoint()).isZero();
            assertThat(status.getProblemPracticeLevel()).isEqualTo(1L);
            assertThat(status.getNotePracticeLevel()).isEqualTo(1L);
        }

        @Test
        @DisplayName("네 능력치 모두 같은 공식을 쓴다")
        void everyAbilityUsesSameFormula() {
            UserMissionStatus attendance = newcomer();
            UserMissionStatus noteWrite = newcomer();
            UserMissionStatus problemPractice = newcomer();
            UserMissionStatus notePractice = newcomer();

            attendance.gainAttendancePoint(35L);
            noteWrite.gainNoteWritePoint(35L);
            problemPractice.gainProblemPracticePoint(35L);
            notePractice.gainNotePracticePoint(35L);

            assertThat(attendance.getAttendanceLevel()).isEqualTo(3L);
            assertThat(noteWrite.getNoteWriteLevel()).isEqualTo(3L);
            assertThat(problemPractice.getProblemPracticeLevel()).isEqualTo(3L);
            assertThat(notePractice.getNotePracticeLevel()).isEqualTo(3L);
            assertThat(attendance.getAttendancePoint()).isEqualTo(5L);
            assertThat(noteWrite.getNoteWritePoint()).isEqualTo(5L);
        }
    }

    @Nested
    @DisplayName("총 학습 레벨")
    class TotalStudyLevel {

        @Test
        @DisplayName("어떤 능력치로 얻은 경험치든 총 학습 포인트에 합산된다")
        void accumulatesPointsFromEveryAbility() {
            UserMissionStatus status = newcomer();

            status.gainAttendancePoint(5L);
            status.gainNoteWritePoint(5L);
            status.gainProblemPracticePoint(5L);
            status.gainNotePracticePoint(5L);

            assertThat(status.getTotalStudyLevel()).isEqualTo(1L);
            assertThat(status.getTotalStudyPoint()).isEqualTo(20L);
        }

        @ParameterizedTest(name = "총 {0}점이면 총 학습 레벨 {1}, 잔여 {2}점")
        @CsvSource({
                " 39, 1, 39",
                " 40, 2,  0",
                " 41, 2,  1",
                "120, 3,  0",
                "240, 4,  0"
        })
        @DisplayName("총 학습 레벨은 개별 필요 경험치의 4배마다 오른다")
        void levelsUpAtFourTimesThreshold(long gained, long expectedLevel, long expectedPoint) {
            UserMissionStatus status = newcomer();

            status.gainAttendancePoint(gained);

            assertThat(status.getTotalStudyLevel()).isEqualTo(expectedLevel);
            assertThat(status.getTotalStudyPoint()).isEqualTo(expectedPoint);
        }

        @Test
        @DisplayName("총 학습 레벨은 15에서 멈추고 그 뒤 경험치는 그대로 쌓인다")
        void stopsAtLevelFifteen() {
            UserMissionStatus status = new UserMissionStatus(1L, 0L, 1L, 0L, 1L, 0L, 1L, 0L, 14L, 0L);

            status.gainAttendancePoint(560L);
            assertThat(status.getTotalStudyLevel())
                    .as("레벨 14→15 에 필요한 560점을 채우면 15가 된다")
                    .isEqualTo(15L);
            assertThat(status.getTotalStudyPoint()).isZero();

            status.gainAttendancePoint(10_000L);

            assertThat(status.getTotalStudyLevel())
                    .as("상한을 넘겨 16레벨이 되면 화면에 없는 레벨이 표시된다")
                    .isEqualTo(15L);
            assertThat(status.getTotalStudyPoint())
                    .as("상한 이후 경험치는 사라지지 않고 그대로 누적된다")
                    .isEqualTo(10_000L);
        }

        @Test
        @DisplayName("개별 능력치는 총 학습 레벨과 달리 상한이 없다")
        void abilityLevelHasNoCap() {
            UserMissionStatus status = newcomer();

            status.gainAttendancePoint(100_000L);

            assertThat(status.getAttendanceLevel()).isGreaterThan(15L);
            assertThat(status.getTotalStudyLevel()).isEqualTo(15L);
        }
    }

    @Nested
    @DisplayName("관리자 수동 설정")
    class ManualOverride {

        @Test
        @DisplayName("능력치별로 레벨과 포인트를 직접 덮어쓴다")
        void overwritesLevelAndPoint() {
            UserMissionStatus status = newcomer();

            status.setAttendanceLevel(9L, 3L);
            status.setNoteWriteLevel(8L, 2L);
            status.setProblemPracticeLevel(7L, 1L);
            status.setNotePracticeLevel(6L, 0L);
            status.setTotalStudyLevel(5L, 4L);

            assertThat(status.getAttendanceLevel()).isEqualTo(9L);
            assertThat(status.getAttendancePoint()).isEqualTo(3L);
            assertThat(status.getNoteWriteLevel()).isEqualTo(8L);
            assertThat(status.getProblemPracticeLevel()).isEqualTo(7L);
            assertThat(status.getNotePracticeLevel()).isEqualTo(6L);
            assertThat(status.getTotalStudyLevel()).isEqualTo(5L);
            assertThat(status.getTotalStudyPoint()).isEqualTo(4L);
        }

        @Test
        @DisplayName("수동으로 올린 레벨 위에서도 경험치 획득이 이어진다")
        void keepsGainingFromOverriddenLevel() {
            UserMissionStatus status = newcomer();
            status.setAttendanceLevel(5L, 0L);

            status.gainAttendancePoint(50L);

            assertThat(status.getAttendanceLevel())
                    .as("레벨 5→6 에 필요한 경험치는 50점이다")
                    .isEqualTo(6L);
            assertThat(status.getAttendancePoint()).isZero();
        }
    }

    @Nested
    @DisplayName("미션 종류별 보상")
    class MissionReward {

        @Test
        @DisplayName("미션 종류마다 정해진 포인트와 능력치가 붙어 있다")
        void mapsMissionTypeToPointAndAbility() {
            assertThat(MissionType.USER_LOGIN.getPoint()).isEqualTo(15L);
            assertThat(MissionType.USER_LOGIN.getAbilityType()).isEqualTo(MissionType.AbilityType.ATTENDANCE);
            assertThat(MissionType.PROBLEM_WRITE.getPoint()).isEqualTo(10L);
            assertThat(MissionType.PROBLEM_WRITE.getAbilityType()).isEqualTo(MissionType.AbilityType.NOTE_WRITE);
            assertThat(MissionType.PROBLEM_PRACTICE.getPoint()).isEqualTo(5L);
            assertThat(MissionType.PROBLEM_PRACTICE.getAbilityType()).isEqualTo(MissionType.AbilityType.PROBLEM_PRACTICE);
            assertThat(MissionType.NOTE_PRACTICE.getPoint()).isEqualTo(15L);
            assertThat(MissionType.NOTE_PRACTICE.getAbilityType()).isEqualTo(MissionType.AbilityType.NOTE_PRACTICE);
        }

        /**
         * {@code MissionLog.missionType} 에는 {@code @Enumerated} 가 없어 ORDINAL 로 저장된다.
         * 즉 <b>enum 선언 순서를 바꾸거나 중간에 상수를 끼워 넣으면 이미 저장된 모든 미션 기록의 종류가 바뀐다.</b>
         * 순서를 건드리는 변경이 조용히 지나가지 않도록 현재 순서를 고정한다.
         */
        @Test
        @DisplayName("MissionType 선언 순서는 DB 에 저장된 값이므로 바뀌면 안 된다")
        void pinsOrdinalOrder() {
            assertThat(MissionType.values())
                    .containsExactly(
                            MissionType.USER_LOGIN,
                            MissionType.PROBLEM_WRITE,
                            MissionType.PROBLEM_PRACTICE,
                            MissionType.NOTE_PRACTICE);
        }
    }
}
