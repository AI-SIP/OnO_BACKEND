package com.aisip.OnO.backend.achievement.service;

import com.aisip.OnO.backend.achievement.dto.AchievementListResponseDto;
import com.aisip.OnO.backend.achievement.dto.AchievementResponseDto;
import com.aisip.OnO.backend.achievement.entity.Achievement;
import com.aisip.OnO.backend.achievement.support.AchievementTestSupport;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.problemsolve.entity.AnswerStatus;
import com.aisip.OnO.backend.studyroom.entity.StudyRoom;
import com.aisip.OnO.backend.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("훈장 서비스")
class AchievementServiceTest extends AchievementTestSupport {

    /** 훈장표의 순서 그대로다. 앱이 이 순서를 그대로 그린다. */
    private static final List<String> ORDERED_KEYS = List.of(
            "first_step", "archivist", "persistence", "phoenix", "dawn_class", "night_owl",
            "perfect_month", "flawless", "organizer", "reviewer", "companion", "cheerleader");

    @Nested
    @DisplayName("목록")
    class Catalog {

        @Test
        @DisplayName("아무것도 안 한 사용자에게도 열두 개가 전부 내려가고, 순서는 훈장표 순서다")
        void returnsAllTwelveInContractOrder() {
            User user = fixtures.createUser();

            AchievementListResponseDto response = achievementService.getAchievements(user.getId());

            assertThat(response.achievements()).hasSize(12);
            assertThat(response.achievements().stream().map(AchievementResponseDto::key))
                    .as("서버가 정한 순서를 앱이 그대로 그린다. 앱은 다시 정렬하지 않는다")
                    .containsExactlyElementsOf(ORDERED_KEYS);
            assertThat(response.achievements()).allMatch(item -> !item.earned());
            assertThat(response.newlyEarned()).isEmpty();
        }

        @Test
        @DisplayName("enum 순서와 훈장표 순서가 어긋나지 않는다")
        void enumOrderMatchesContract() {
            assertThat(java.util.Arrays.stream(Achievement.values()).map(Achievement::getKey))
                    .containsExactlyElementsOf(ORDERED_KEYS);
        }

        @Test
        @DisplayName("이미지 경로는 앱 번들 경로다")
        void imageUrlIsBundlePath() {
            User user = fixtures.createUser();

            assertThat(itemOf(user, "archivist").imageUrl()).isEqualTo("assets/Medal/archivist.png");
        }
    }

    @Nested
    @DisplayName("판정과 적립")
    class Earning {

        @Test
        @DisplayName("조건을 채우면 받고 newlyEarned 에 실린다")
        void earnsWhenConditionMet() {
            User user = fixtures.createUser();
            saveProblem(user.getId());

            AchievementListResponseDto response = achievementService.getAchievements(user.getId());

            assertThat(response.newlyEarned()).containsExactly("first_step");
            assertThat(itemOf(response, "first_step").earned()).isTrue();
            assertThat(itemOf(response, "first_step").earnedAt()).isNotNull();
            assertThat(achievementRowCount(user.getId())).isEqualTo(1);
        }

        @Test
        @DisplayName("연달아 두 번 부르면 두 번째는 newlyEarned 가 비고 행도 안 늘어난다")
        void isIdempotent() {
            User user = fixtures.createUser();
            saveProblem(user.getId());

            AchievementListResponseDto first = achievementService.getAchievements(user.getId());
            AchievementListResponseDto second = achievementService.getAchievements(user.getId());

            assertThat(first.newlyEarned()).containsExactly("first_step");
            assertThat(second.newlyEarned()).isEmpty();
            assertThat(achievementRowCount(user.getId())).isEqualTo(1);
            assertThat(itemOf(second, "first_step").earned()).isTrue();
        }

        @Test
        @DisplayName("받은 날짜는 두 번째 호출에서 덮어쓰이지 않는다")
        void keepsFirstEarnedAt() {
            User user = fixtures.createUser();
            saveProblem(user.getId());

            LocalDateTime earnedAt = itemOf(achievementService.getAchievements(user.getId()), "first_step").earnedAt();
            jdbcTemplate.update("UPDATE user_achievement SET earned_at = ? WHERE user_id = ?",
                    LocalDateTime.of(2020, 1, 1, 0, 0), user.getId());

            assertThat(earnedAt).isNotNull();
            assertThat(itemOf(achievementService.getAchievements(user.getId()), "first_step").earnedAt())
                    .as("이미 있는 행은 건드리지 않는다. 덮어쓰면 화면을 열 때마다 받은 날이 오늘로 바뀐다")
                    .isEqualTo(LocalDateTime.of(2020, 1, 1, 0, 0));
        }

        @Test
        @DisplayName("받은 뒤 데이터를 지워도 훈장은 남는다")
        void neverRevoked() {
            User user = fixtures.createUser();
            Problem problem = saveProblem(user.getId());
            achievementService.getAchievements(user.getId());

            problemRepository.delete(problem);

            AchievementListResponseDto response = achievementService.getAchievements(user.getId());
            assertThat(itemOf(response, "first_step").earned())
                    .as("오답노트를 지웠다고 기록광을 뺏으면 지우는 것이 무서워진다")
                    .isTrue();
            assertThat(response.newlyEarned()).isEmpty();
        }

        @Test
        @DisplayName("기록광은 오답노트 백 개에서 열린다 - 경계는 포함이다")
        void archivistBoundaryIsInclusive() {
            User user = fixtures.createUser();
            saveProblems(user.getId(), 99);
            assertThat(itemOf(user, "archivist").earned()).isFalse();

            saveProblem(user.getId());

            assertThat(itemOf(user, "archivist").earned()).isTrue();
        }

        @Test
        @DisplayName("남의 데이터는 내 훈장에 안 잡힌다")
        void countsOnlyOwnData() {
            User user = fixtures.createUser();
            User other = fixtures.createOtherUser();
            saveProblems(other.getId(), 3);

            AchievementListResponseDto response = achievementService.getAchievements(user.getId());

            assertThat(itemOf(response, "first_step").earned()).isFalse();
            assertThat(itemOf(response, "archivist").current()).isZero();
        }
    }

    @Nested
    @DisplayName("진행도")
    class Progress {

        @Test
        @DisplayName("잠긴 훈장도 지금 몇까지 왔는지 함께 내려준다")
        void showsCurrentAndTarget() {
            User user = fixtures.createUser();
            saveFolders(user.getId(), 4);

            AchievementResponseDto organizer = itemOf(user, "organizer");

            assertThat(organizer.earned()).isFalse();
            assertThat(organizer.current()).isEqualTo(4);
            assertThat(organizer.target()).isEqualTo(10);
        }

        @Test
        @DisplayName("목표치를 넘어도 목표치로 잘라서 준다")
        void clampsToTarget() {
            User user = fixtures.createUser();
            saveFolders(user.getId(), 13);

            AchievementResponseDto organizer = itemOf(user, "organizer");

            assertThat(organizer.earned()).isTrue();
            assertThat(organizer.current()).isEqualTo(10);
            assertThat(organizer.target()).isEqualTo(10);
        }

        @Test
        @DisplayName("불사조와 첫 걸음은 진행도가 null 이다")
        void noProgressForBinaryAchievements() {
            User user = fixtures.createUser();
            saveProblem(user.getId());

            AchievementListResponseDto response = achievementService.getAchievements(user.getId());

            assertThat(itemOf(response, "first_step").current()).isNull();
            assertThat(itemOf(response, "first_step").target()).isNull();
            assertThat(itemOf(response, "phoenix").current()).isNull();
            assertThat(itemOf(response, "phoenix").target()).isNull();
        }
    }

    @Nested
    @DisplayName("새벽반 · 올빼미")
    class TimeOfDay {

        @ParameterizedTest(name = "{0}시 {1}분 복습은 새벽반 {2} / 올빼미 {3}")
        @CsvSource({
                "0, 0, 0, 1",
                "2, 59, 0, 1",
                "3, 0, 0, 0",
                "4, 59, 0, 0",
                "5, 0, 1, 0",
                "7, 59, 1, 0",
                "8, 0, 0, 0",
                "23, 59, 0, 0",
        })
        @DisplayName("경계는 KST 시각으로 05:00~08:00 과 00:00~03:00 이다")
        void countsByKstHour(int hour, int minute, long dawn, long night) {
            User user = fixtures.createUser();
            Problem problem = saveProblem(user.getId());
            saveSolve(problem, user.getId(), LocalDateTime.of(2026, 3, 2, hour, minute), AnswerStatus.CORRECT);

            AchievementListResponseDto response = achievementService.getAchievements(user.getId());

            assertThat(itemOf(response, "dawn_class").current()).isEqualTo(dawn);
            assertThat(itemOf(response, "night_owl").current()).isEqualTo(night);
        }

        @Test
        @DisplayName("새벽 열 번이면 받는다")
        void earnsDawnClassAtTen() {
            User user = fixtures.createUser();
            Problem problem = saveProblem(user.getId());
            for (int i = 0; i < 10; i++) {
                saveSolve(problem, user.getId(),
                        LocalDateTime.of(2026, 3, 2, 6, 0).plusDays(i), AnswerStatus.CORRECT);
            }

            assertThat(itemOf(user, "dawn_class").earned()).isTrue();
        }
    }

    @Nested
    @DisplayName("무결점")
    class Flawless {

        @Test
        @DisplayName("UNKNOWN 에서 끊기지 않는다")
        void unknownDoesNotBreakStreak() {
            User user = fixtures.createUser();
            Problem problem = saveProblem(user.getId());
            saveSolveSequence(problem, user.getId(), NOON,
                    AnswerStatus.CORRECT, AnswerStatus.CORRECT, AnswerStatus.CORRECT, AnswerStatus.CORRECT,
                    AnswerStatus.CORRECT, AnswerStatus.UNKNOWN,
                    AnswerStatus.CORRECT, AnswerStatus.CORRECT, AnswerStatus.CORRECT, AnswerStatus.CORRECT,
                    AnswerStatus.CORRECT);

            assertThat(itemOf(user, "flawless").earned())
                    .as("UNKNOWN 은 그때 맞혔는지 모르는 것이라 틀렸다고 보면 없는 실패를 만드는 셈이다")
                    .isTrue();
        }

        @Test
        @DisplayName("오답에서 끊긴다")
        void wrongBreaksStreak() {
            User user = fixtures.createUser();
            Problem problem = saveProblem(user.getId());
            saveSolveSequence(problem, user.getId(), NOON,
                    AnswerStatus.CORRECT, AnswerStatus.CORRECT, AnswerStatus.CORRECT, AnswerStatus.CORRECT,
                    AnswerStatus.CORRECT, AnswerStatus.WRONG,
                    AnswerStatus.CORRECT, AnswerStatus.CORRECT, AnswerStatus.CORRECT, AnswerStatus.CORRECT,
                    AnswerStatus.CORRECT);

            AchievementResponseDto flawless = itemOf(user, "flawless");
            assertThat(flawless.earned()).isFalse();
            assertThat(flawless.current()).isEqualTo(5);
        }

        @Test
        @DisplayName("부분 정답에서도 끊긴다")
        void partialBreaksStreak() {
            User user = fixtures.createUser();
            Problem problem = saveProblem(user.getId());
            saveSolveSequence(problem, user.getId(), NOON,
                    AnswerStatus.CORRECT, AnswerStatus.CORRECT, AnswerStatus.CORRECT, AnswerStatus.CORRECT,
                    AnswerStatus.CORRECT, AnswerStatus.PARTIAL,
                    AnswerStatus.CORRECT, AnswerStatus.CORRECT, AnswerStatus.CORRECT, AnswerStatus.CORRECT,
                    AnswerStatus.CORRECT);

            assertThat(itemOf(user, "flawless").earned()).isFalse();
        }
    }

    @Nested
    @DisplayName("불사조")
    class Phoenix {

        @Test
        @DisplayName("오답 뒤에 정답이면 받는다")
        void wrongThenCorrect() {
            User user = fixtures.createUser();
            Problem problem = saveProblem(user.getId());
            saveSolveSequence(problem, user.getId(), NOON, AnswerStatus.WRONG, AnswerStatus.CORRECT);

            assertThat(itemOf(user, "phoenix").earned()).isTrue();
        }

        @Test
        @DisplayName("정답 뒤 오답 순서에서는 안 걸린다")
        void correctThenWrongDoesNotCount() {
            User user = fixtures.createUser();
            Problem problem = saveProblem(user.getId());
            saveSolveSequence(problem, user.getId(), NOON, AnswerStatus.CORRECT, AnswerStatus.WRONG);

            assertThat(itemOf(user, "phoenix").earned())
                    .as("practicedAt 순서를 본다. 틀린 뒤에 맞힌 것만 불사조다")
                    .isFalse();
        }

        @Test
        @DisplayName("부분 정답은 맞힌 것으로 치지 않는다")
        void partialIsNotCorrect() {
            User user = fixtures.createUser();
            Problem problem = saveProblem(user.getId());
            saveSolveSequence(problem, user.getId(), NOON, AnswerStatus.WRONG, AnswerStatus.PARTIAL);

            assertThat(itemOf(user, "phoenix").earned()).isFalse();
        }

        @Test
        @DisplayName("다른 문제에서 맞힌 것은 안 친다")
        void differentProblemDoesNotCount() {
            User user = fixtures.createUser();
            saveSolve(saveProblem(user.getId()), user.getId(), NOON, AnswerStatus.WRONG);
            saveSolve(saveProblem(user.getId()), user.getId(), NOON.plusHours(1), AnswerStatus.CORRECT);

            assertThat(itemOf(user, "phoenix").earned()).isFalse();
        }
    }

    @Nested
    @DisplayName("집념")
    class Persistence {

        @Test
        @DisplayName("한 문제를 다섯 번 보면 받는다")
        void sameProblemFiveTimes() {
            User user = fixtures.createUser();
            Problem problem = saveProblem(user.getId());
            saveSolveSequence(problem, user.getId(), NOON,
                    AnswerStatus.WRONG, AnswerStatus.WRONG, AnswerStatus.WRONG, AnswerStatus.WRONG, AnswerStatus.CORRECT);

            assertThat(itemOf(user, "persistence").earned()).isTrue();
        }

        @Test
        @DisplayName("다섯 문제를 한 번씩 본 것은 다르다")
        void fiveProblemsOnceEachDoesNotCount() {
            User user = fixtures.createUser();
            for (int i = 0; i < 5; i++) {
                saveSolve(saveProblem(user.getId()), user.getId(), NOON.plusHours(i), AnswerStatus.WRONG);
            }

            AchievementResponseDto persistence = itemOf(user, "persistence");
            assertThat(persistence.earned()).isFalse();
            assertThat(persistence.current()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("개근")
    class PerfectMonth {

        @Test
        @DisplayName("연속 서른 날이면 받는다")
        void thirtyConsecutiveDays() {
            User user = fixtures.createUser();
            saveLoginDays(user, LocalDate.of(2026, 1, 1), 30);

            assertThat(itemOf(user, "perfect_month").earned()).isTrue();
        }

        @Test
        @DisplayName("스물아홉 날에서 하루 빠지면 안 받는다")
        void gapBreaksStreak() {
            User user = fixtures.createUser();
            saveLoginDays(user, LocalDate.of(2026, 1, 1), 29);
            saveLoginDays(user, LocalDate.of(2026, 2, 1), 20);

            AchievementResponseDto perfectMonth = itemOf(user, "perfect_month");
            assertThat(perfectMonth.earned()).isFalse();
            assertThat(perfectMonth.current())
                    .as("가장 길었던 구간을 본다. 지금 이어지는 20 일이 아니라 지난달의 29 일이다")
                    .isEqualTo(29);
        }

        @Test
        @DisplayName("하루에 여러 번 로그인해도 하루로 센다")
        void multipleLoginsInOneDayCountOnce() {
            User user = fixtures.createUser();
            for (int i = 0; i < 30; i++) {
                saveLoginAt(user, LocalDate.of(2026, 1, 1).atTime(9, 0));
            }

            assertThat(itemOf(user, "perfect_month").current()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("회고왕")
    class Reviewer {

        @Test
        @DisplayName("공백만 적은 회고는 안 센다")
        void blankReflectionDoesNotCount() {
            User user = fixtures.createUser();
            Problem problem = saveProblem(user.getId());
            saveSolve(problem, user.getId(), NOON, AnswerStatus.CORRECT, "적었다");
            saveSolve(problem, user.getId(), NOON.plusHours(1), AnswerStatus.CORRECT, "   ");
            saveSolve(problem, user.getId(), NOON.plusHours(2), AnswerStatus.CORRECT, null);

            assertThat(itemOf(user, "reviewer").current()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("동행 · 응원단장")
    class StudyRoomAchievements {

        @Test
        @DisplayName("스터디룸 한 곳에 들어가면 동행을 받는다")
        void joiningOneRoomEarnsCompanion() {
            User user = fixtures.createUser();
            joinNewRoom(user);

            assertThat(itemOf(user, "companion").earned()).isTrue();
        }

        @Test
        @DisplayName("응원은 리액션 세 자리를 합쳐 센다")
        void reactionsAreSummedAcrossThreeTables() {
            User user = fixtures.createUser();
            StudyRoom room = joinNewRoom(user);
            saveFeedReactions(user, room, 40);
            saveSharedProblemReactions(user, room, 40);
            saveCommentReactions(user, room, 20);

            assertThat(itemOf(user, "cheerleader").earned())
                    .as("누른 자리가 어디든 응원한 것은 응원한 것이다")
                    .isTrue();
        }

        @Test
        @DisplayName("합쳐도 백에 못 미치면 진행도만 오른다")
        void reactionsBelowThreshold() {
            User user = fixtures.createUser();
            StudyRoom room = joinNewRoom(user);
            saveFeedReactions(user, room, 3);
            saveCommentReactions(user, room, 2);

            AchievementResponseDto cheerleader = itemOf(user, "cheerleader");
            assertThat(cheerleader.earned()).isFalse();
            assertThat(cheerleader.current()).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("쿼리 수")
    class QueryCount {

        /**
         * 훈장 화면을 열 때마다 도는 경로다. 열두 조건을 각각 세면 열둘이 넘는데, 여섯이 같은
         * 복습 기록 표를 보므로 그 표를 한 번만 읽는다. 이 숫자가 늘면 조건을 하나씩 세기 시작한 것이다.
         */
        @Test
        @DisplayName("조회 한 번에 아홉 번 나간다")
        void countsNineQueries() {
            User user = fixtures.createUser();
            saveProblems(user.getId(), 2);
            saveFolders(user.getId(), 2);
            Problem problem = saveProblem(user.getId());
            saveSolveSequence(problem, user.getId(), NOON, AnswerStatus.WRONG, AnswerStatus.CORRECT);
            saveLoginDays(user, LocalDate.of(2026, 1, 1), 2);
            // 새로 받을 훈장이 없는 정상 상태를 잰다. 첫 호출은 INSERT 가 섞여 숫자가 달라진다.
            achievementService.getAchievements(user.getId());

            long queryCount = queryCounter.count(() -> achievementService.getAchievements(user.getId()));

            assertThat(queryCount).isEqualTo(9);
        }
    }

    // ─────────────────────────── 도우미 ───────────────────────────

    private AchievementResponseDto itemOf(User user, String key) {
        return itemOf(achievementService.getAchievements(user.getId()), key);
    }

    private AchievementResponseDto itemOf(AchievementListResponseDto response, String key) {
        return response.achievements().stream()
                .filter(item -> item.key().equals(key))
                .findFirst()
                .orElseThrow(() -> new AssertionError("훈장 " + key + " 가 응답에 없다"));
    }
}
