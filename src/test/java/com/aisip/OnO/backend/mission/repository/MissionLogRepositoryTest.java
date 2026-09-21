package com.aisip.OnO.backend.mission.repository;

import com.aisip.OnO.backend.mission.entity.MissionLog;
import com.aisip.OnO.backend.mission.entity.MissionType;
import com.aisip.OnO.backend.mission.support.MissionTestSupport;
import com.aisip.OnO.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MissionLogRepository")
class MissionLogRepositoryTest extends MissionTestSupport {

    private User user;
    private User other;

    @BeforeEach
    void setUpUsers() {
        user = fixtures.createUser();
        other = fixtures.createOtherUser();
    }

    @Nested
    @DisplayName("오늘 로그인 여부")
    class AlreadyLogin {

        @Test
        @DisplayName("기록이 없으면 아직 로그인하지 않은 것으로 본다")
        void falseWhenNoLog() {
            assertThat(missionLogRepository.alreadyLogin(user.getId())).isFalse();
        }

        @Test
        @DisplayName("오늘 로그인 기록이 있으면 true 다")
        void trueWhenLoggedInToday() {
            saveMissionLog(user, MissionType.USER_LOGIN, null);

            assertThat(missionLogRepository.alreadyLogin(user.getId())).isTrue();
        }

        @Test
        @DisplayName("다른 사용자의 로그인 기록은 내 로그인으로 세지 않는다")
        void isolatesByUser() {
            saveMissionLog(other, MissionType.USER_LOGIN, null);

            assertThat(missionLogRepository.alreadyLogin(user.getId()))
                    .as("남의 출석으로 내 출석이 소진되면 하루치 보상을 잃는다")
                    .isFalse();
            assertThat(missionLogRepository.alreadyLogin(other.getId())).isTrue();
        }

        @Test
        @DisplayName("로그인 이외의 미션은 로그인으로 세지 않는다")
        void isolatesByMissionType() {
            saveMissionLog(user, MissionType.PROBLEM_WRITE, null);

            assertThat(missionLogRepository.alreadyLogin(user.getId())).isFalse();
        }

        @Test
        @DisplayName("자정 직후 기록은 오늘로, 어제 자정 직전 기록은 어제로 본다")
        void respectsMidnightBoundary() {
            saveMissionLogAt(user, MissionType.USER_LOGIN, null, today().minusDays(1).atTime(23, 59, 59));
            assertThat(missionLogRepository.alreadyLogin(user.getId()))
                    .as("어제 23:59:59 로그인은 오늘 출석이 아니다")
                    .isFalse();

            saveMissionLogAt(other, MissionType.USER_LOGIN, null, today().atStartOfDay());
            assertThat(missionLogRepository.alreadyLogin(other.getId()))
                    .as("오늘 00:00:00 로그인은 오늘 출석이다")
                    .isTrue();
        }

        @Test
        @DisplayName("오늘 23:59:59 기록도 오늘로 본다")
        void includesEndOfToday() {
            saveMissionLogAt(user, MissionType.USER_LOGIN, null, today().atTime(23, 59, 59));

            assertThat(missionLogRepository.alreadyLogin(user.getId())).isTrue();
        }

        /**
         * 예전 구현은 {@code between(오늘 00:00, 오늘 23:59:59.999999999)} 였다.
         * MySQL DATETIME(6) 은 마이크로초까지만 담으므로 끝값이 <b>내일 00:00:00 으로 반올림</b>되고,
         * BETWEEN 은 양끝을 포함하기 때문에 자정 정각 기록이 전날에도 오늘로 잡혔다.
         * 그러면 자정에 로그인한 사용자는 전날 출석이 이미 있는 것으로 판정돼 보상을 잃는다.
         */
        @Test
        @DisplayName("내일 자정 정각 기록은 오늘로 세지 않는다")
        void excludesTomorrowMidnight() {
            saveMissionLogAt(user, MissionType.USER_LOGIN, null, today().plusDays(1).atStartOfDay());

            assertThat(missionLogRepository.alreadyLogin(user.getId()))
                    .as("하루의 끝을 23:59:59.999999999 로 잡으면 자정 정각이 양쪽 날짜에 모두 포함된다")
                    .isFalse();
        }
    }

    @Nested
    @DisplayName("오늘 작성한 문제 수")
    class ProblemWritesToday {

        @ParameterizedTest(name = "오늘 {0}건 작성")
        @ValueSource(ints = {0, 1, 2, 3, 5})
        @DisplayName("오늘 작성한 PROBLEM_WRITE 미션 수를 센다")
        void countsProblemWritesToday(int count) {
            for (int i = 0; i < count; i++) {
                saveMissionLog(user, MissionType.PROBLEM_WRITE, null);
            }

            assertThat(missionLogRepository.countProblemWritesToday(user.getId())).isEqualTo(count);
            assertThat(missionLogRepository.alreadyWriteProblemsTodayMoreThan3(user.getId()))
                    .as("하루 3건이 상한이다")
                    .isEqualTo(count >= 3);
        }

        @Test
        @DisplayName("다른 사용자의 작성 기록은 세지 않는다")
        void isolatesByUser() {
            saveMissionLog(other, MissionType.PROBLEM_WRITE, null);
            saveMissionLog(other, MissionType.PROBLEM_WRITE, null);
            saveMissionLog(other, MissionType.PROBLEM_WRITE, null);

            assertThat(missionLogRepository.countProblemWritesToday(user.getId())).isZero();
            assertThat(missionLogRepository.alreadyWriteProblemsTodayMoreThan3(user.getId())).isFalse();
        }

        @Test
        @DisplayName("어제 작성한 기록은 오늘 상한에 포함되지 않는다")
        void resetsAtMidnight() {
            for (int i = 0; i < 3; i++) {
                saveMissionLogAt(user, MissionType.PROBLEM_WRITE, null, today().minusDays(1).atTime(20, 0));
            }

            assertThat(missionLogRepository.countProblemWritesToday(user.getId()))
                    .as("어제 채운 상한이 오늘까지 이어지면 보상이 영영 막힌다")
                    .isZero();
        }
    }

    @Nested
    @DisplayName("복습 미션 중복 여부")
    class PracticeDuplication {

        @Test
        @DisplayName("같은 문제를 오늘 이미 복습했으면 true 다")
        void detectsProblemPracticedToday() {
            saveMissionLog(user, MissionType.PROBLEM_PRACTICE, 42L);

            assertThat(missionLogRepository.alreadyPracticeProblem(42L)).isTrue();
            assertThat(missionLogRepository.alreadyPracticeProblem(43L))
                    .as("다른 문제까지 막히면 안 된다")
                    .isFalse();
        }

        @Test
        @DisplayName("어제 복습한 문제는 오늘 다시 보상을 받을 수 있다")
        void allowsPracticeAgainNextDay() {
            saveMissionLogAt(user, MissionType.PROBLEM_PRACTICE, 42L, today().minusDays(1).atTime(10, 0));

            assertThat(missionLogRepository.alreadyPracticeProblem(42L)).isFalse();
        }

        @Test
        @DisplayName("복습노트도 같은 방식으로 하루 한 번만 인정한다")
        void detectsNotePracticedToday() {
            saveMissionLog(user, MissionType.NOTE_PRACTICE, 7L);

            assertThat(missionLogRepository.alreadyPracticeNote(7L)).isTrue();
            assertThat(missionLogRepository.alreadyPracticeNote(8L)).isFalse();
        }

        @Test
        @DisplayName("문제 복습과 복습노트는 referenceId 가 같아도 서로 간섭하지 않는다")
        void separatesProblemAndNoteReference() {
            saveMissionLog(user, MissionType.PROBLEM_PRACTICE, 5L);

            assertThat(missionLogRepository.alreadyPracticeNote(5L))
                    .as("미션 종류를 구분하지 않으면 id 가 겹치는 순간 보상이 사라진다")
                    .isFalse();
        }

        @Test
        @DisplayName("중복 판정은 referenceId 로만 한다 - 사용자별로 나누지 않는다")
        void deduplicatesByReferenceIdOnly() {
            saveMissionLog(other, MissionType.PROBLEM_PRACTICE, 42L);

            assertThat(missionLogRepository.alreadyPracticeProblem(42L))
                    .as("""
                            문제/복습노트 id 는 전역 유일하고 한 사용자에게만 속하므로 현재 구조에서는 문제가 없다.
                            id 체계가 사용자별로 바뀌면 남의 복습이 내 보상을 막게 된다는 점을 드러내 둔다.""")
                    .isTrue();
        }
    }

    @Nested
    @DisplayName("오늘 획득 포인트 합")
    class PointSumToday {

        @Test
        @DisplayName("기록이 없으면 null 이 아니라 0 을 준다")
        void returnsZeroWhenNoLog() {
            assertThat(missionLogRepository.getPointSumToday(user.getId()))
                    .as("null 이 나오면 곧바로 NPE 로 이어진다")
                    .isEqualTo(0L);
        }

        @Test
        @DisplayName("미션 종류에 관계없이 오늘 받은 포인트를 모두 더한다")
        void sumsEveryMissionTypeToday() {
            saveMissionLog(user, MissionType.USER_LOGIN, null);       // 15
            saveMissionLog(user, MissionType.PROBLEM_WRITE, null);    // 10
            saveMissionLog(user, MissionType.PROBLEM_PRACTICE, 1L);   // 5
            saveMissionLog(user, MissionType.NOTE_PRACTICE, 1L);      // 15

            assertThat(missionLogRepository.getPointSumToday(user.getId())).isEqualTo(45L);
        }

        @Test
        @DisplayName("다른 사용자의 포인트는 더하지 않는다")
        void isolatesByUser() {
            saveMissionLog(other, MissionType.USER_LOGIN, null);

            assertThat(missionLogRepository.getPointSumToday(user.getId())).isEqualTo(0L);
        }

        @Test
        @DisplayName("어제 받은 포인트는 오늘 합계에 들어가지 않는다")
        void excludesYesterday() {
            saveMissionLogAt(user, MissionType.USER_LOGIN, null, today().minusDays(1).atTime(12, 0));

            assertThat(missionLogRepository.getPointSumToday(user.getId())).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("사용자별 미션 기록 조회")
    class FindAllByUserId {

        @Test
        @DisplayName("본인 기록만 돌려준다")
        void returnsOnlyOwnLogs() {
            MissionLog mine = saveMissionLog(user, MissionType.USER_LOGIN, null);
            saveMissionLog(other, MissionType.USER_LOGIN, null);

            assertThat(missionLogRepository.findAllByUserId(user.getId()))
                    .extracting(MissionLog::getId)
                    .containsExactly(mine.getId());
        }

        @Test
        @DisplayName("기록이 없으면 빈 목록이다")
        void returnsEmptyListWhenNoLog() {
            assertThat(missionLogRepository.findAllByUserId(user.getId())).isEmpty();
        }
    }

    @Nested
    @DisplayName("기간 집계")
    class PeriodAggregation {

        @Test
        @DisplayName("일자별 출석자 수는 기록이 없는 날도 0으로 채운다")
        void fillsMissingDatesWithZero() {
            LocalDate end = today();
            LocalDate start = end.minusDays(4);

            Map<LocalDate, Long> result = missionLogRepository.getDailyActiveUsersCount(start, end);

            assertThat(result).hasSize(5);
            assertThat(result.values()).containsOnly(0L);
            assertThat(result.keySet())
                    .as("차트가 최신 날짜부터 그려지도록 내림차순으로 준다")
                    .containsExactly(end, end.minusDays(1), end.minusDays(2), end.minusDays(3), start);
        }

        @Test
        @DisplayName("같은 날 여러 번 로그인해도 출석자는 1명으로 센다")
        void countsDistinctUsersPerDay() {
            saveMissionLog(user, MissionType.USER_LOGIN, null);
            saveMissionLog(user, MissionType.USER_LOGIN, null);
            saveMissionLog(other, MissionType.USER_LOGIN, null);

            Map<LocalDate, Long> result = missionLogRepository.getDailyActiveUsersCount(today(), today());

            assertThat(result.get(today())).isEqualTo(2L);
        }

        @Test
        @DisplayName("일수를 넘겨도 오늘을 끝으로 하는 구간을 만든다")
        void buildsRangeFromDayCount() {
            Map<LocalDate, Long> result = missionLogRepository.getDailyActiveUsersCount(7);

            assertThat(result).hasSize(7);
            assertThat(result.keySet()).first().isEqualTo(today());
            assertThat(result.keySet()).last().isEqualTo(today().minusDays(6));
        }

        @Test
        @DisplayName("특정 날짜의 출석 사용자를 중복 없이 돌려준다")
        void returnsDistinctActiveUsersOfDate() {
            saveMissionLog(user, MissionType.USER_LOGIN, null);
            saveMissionLog(user, MissionType.USER_LOGIN, null);
            saveMissionLog(other, MissionType.PROBLEM_WRITE, null);

            List<User> activeUsers = missionLogRepository.getActiveUsersByDate(today());

            assertThat(activeUsers)
                    .extracting(User::getId)
                    .as("문제 작성은 출석이 아니다")
                    .containsExactly(user.getId());
        }

        @Test
        @DisplayName("미션 종류별 전체 건수를 센다")
        void countsByMissionType() {
            saveMissionLog(user, MissionType.NOTE_PRACTICE, 1L);
            saveMissionLog(user, MissionType.NOTE_PRACTICE, 2L);
            saveMissionLog(user, MissionType.USER_LOGIN, null);

            assertThat(missionLogRepository.countByMissionType(MissionType.NOTE_PRACTICE)).isEqualTo(2);
            assertThat(missionLogRepository.countByMissionType(MissionType.PROBLEM_PRACTICE)).isZero();
        }

        @Test
        @DisplayName("기간 안의 순 방문자 수를 센다")
        void countsDistinctVisitorsInRange() {
            saveMissionLog(user, MissionType.USER_LOGIN, null);
            saveMissionLog(user, MissionType.USER_LOGIN, null);
            saveMissionLogAt(other, MissionType.USER_LOGIN, null, today().minusDays(40).atTime(12, 0));

            long count = missionLogRepository.countDistinctUsersByMissionTypeAndCreatedAtBetween(
                    MissionType.USER_LOGIN,
                    today().minusDays(6).atStartOfDay(),
                    today().atTime(LocalTime.MAX));

            assertThat(count)
                    .as("기간 밖 방문자까지 세면 지표가 부풀려진다")
                    .isEqualTo(1L);
        }

        @Test
        @DisplayName("일자별 미션 건수를 날짜별로 묶어 돌려준다")
        void groupsDailyCountsByDate() {
            saveMissionLog(user, MissionType.NOTE_PRACTICE, 1L);
            saveMissionLog(other, MissionType.NOTE_PRACTICE, 2L);
            saveMissionLogAt(user, MissionType.NOTE_PRACTICE, 3L, today().minusDays(1).atTime(10, 0));

            List<Object[]> rows = missionLogRepository.countDailyByMissionType(
                    MissionType.NOTE_PRACTICE,
                    today().minusDays(1).atStartOfDay(),
                    today().atTime(LocalTime.MAX));

            assertThat(rows).hasSize(2);
            assertThat(rows).extracting(row -> (Long) row[1]).containsExactlyInAnyOrder(2L, 1L);
        }

        @Test
        @DisplayName("페이지 크기만큼 끊어 사용자와 함께 조회한다")
        void findsWithUserByPage() {
            saveMissionLog(user, MissionType.NOTE_PRACTICE, 1L);
            saveMissionLog(other, MissionType.NOTE_PRACTICE, 2L);
            saveMissionLog(user, MissionType.USER_LOGIN, null);

            List<MissionLog> logs = missionLogRepository.findAllByMissionTypeWithUser(
                    MissionType.NOTE_PRACTICE, PageRequest.of(0, 1));

            assertThat(logs).hasSize(1);
            assertThat(logs.get(0).getUser().getId())
                    .as("사용자를 함께 가져오지 않으면 관리자 화면에서 지연 로딩 예외가 난다")
                    .isNotNull();
        }
    }

    @Nested
    @DisplayName("소프트 삭제")
    class SoftDelete {

        @Test
        @DisplayName("삭제한 미션 기록은 어떤 조회에도 나타나지 않는다")
        void excludesSoftDeletedLogs() {
            MissionLog missionLog = saveMissionLog(user, MissionType.USER_LOGIN, null);
            missionLogRepository.delete(missionLog);

            assertThat(missionLogRepository.findAllByUserId(user.getId())).isEmpty();
            assertThat(missionLogRepository.alreadyLogin(user.getId()))
                    .as("소프트 삭제된 기록이 중복 판정에 남으면 보상이 영영 막힌다")
                    .isFalse();
            assertThat(missionLogRepository.getPointSumToday(user.getId())).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("시간대 기준")
    class TimeZoneBaseline {

        /**
         * {@code MissionLogRepositoryImpl} 의 "오늘"은 {@code LocalDate.now()}, 즉 JVM 기본 시간대다.
         * 반면 problem/studyroom/learningreport 등 다른 도메인은 {@code ZoneId.of("Asia/Seoul")} 을 명시한다.
         * {@code created_at} 역시 JPA Auditing 이 JVM 기본 시간대로 채우므로 미션 도메인 안에서는 일관되지만,
         * JVM 시간대가 KST 가 아닌 환경에서는 다른 도메인과 하루 경계가 어긋난다.
         * 이 전제가 깨지는 변경을 잡기 위해 기준을 고정한다.
         */
        @Test
        @DisplayName("중복 판정의 하루 경계는 created_at 과 같은 시간대 기준으로 움직인다")
        void usesSameZoneAsCreatedAt() {
            saveMissionLog(user, MissionType.USER_LOGIN, null);

            LocalDateTime createdAt = missionLogRepository.findAllByUserId(user.getId()).get(0).getCreatedAt();

            assertThat(createdAt.toLocalDate())
                    .as("created_at 의 날짜와 오늘 판정 기준이 어긋나면 중복 방지가 통째로 어긋난다")
                    .isEqualTo(LocalDate.now());
            assertThat(missionLogRepository.alreadyLogin(user.getId())).isTrue();
        }
    }
}
