package com.aisip.OnO.backend.mission.service;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.mission.dto.MissionClaimResponseDto;
import com.aisip.OnO.backend.mission.dto.MissionListResponseDto;
import com.aisip.OnO.backend.mission.dto.MissionResponseDto;
import com.aisip.OnO.backend.mission.entity.MissionCategory;
import com.aisip.OnO.backend.mission.entity.MissionMetric;
import com.aisip.OnO.backend.mission.entity.MissionProgress;
import com.aisip.OnO.backend.mission.entity.MissionRewardType;
import com.aisip.OnO.backend.mission.entity.MissionType;
import com.aisip.OnO.backend.mission.entity.UserMissionStatus;
import com.aisip.OnO.backend.mission.exception.MissionErrorCase;
import com.aisip.OnO.backend.mission.support.MissionSystemTestSupport;
import com.aisip.OnO.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("미션 조회와 보상 받기")
class MissionServiceTest extends MissionSystemTestSupport {

    private User user;

    @BeforeEach
    void setUpUser() {
        user = fixtures.createUser();
    }

    @Nested
    @DisplayName("목록 조회")
    class GetMissions {

        @Test
        @DisplayName("시드 10종이 일일 6개와 주간 4개로 갈린다")
        void splitsDailyAndWeekly() {
            MissionListResponseDto response = missionService.getMissions(user.getId());

            assertThat(response.daily().missions()).hasSize(6);
            assertThat(response.weekly().missions()).hasSize(4);
            assertThat(response.daily().missions())
                    .extracting(MissionResponseDto::category)
                    .containsOnly(MissionCategory.DAILY);
            assertThat(response.weekly().missions())
                    .extracting(MissionResponseDto::category)
                    .containsOnly(MissionCategory.WEEKLY);
        }

        @Test
        @DisplayName("기간 키를 각 묶음에 실어 내려준다")
        void carriesPeriodKeys() {
            MissionListResponseDto response = missionService.getMissions(user.getId());

            assertThat(response.daily().periodKey()).isEqualTo(MissionPeriodKey.daily(MissionPeriodKey.today()));
            assertThat(response.weekly().periodKey()).isEqualTo(MissionPeriodKey.weekly(MissionPeriodKey.today()));
        }

        @Test
        @DisplayName("sortOrder 오름차순으로 정렬된다")
        void sortsBySortOrder() {
            MissionListResponseDto response = missionService.getMissions(user.getId());

            assertThat(response.daily().missions())
                    .extracting(MissionResponseDto::code)
                    .containsExactly(
                            DAILY_ATTEND, DAILY_NOTE_WRITE, DAILY_REVIEW_3,
                            DAILY_CORRECT_3, DAILY_PRACTICE_SET, DAILY_MOOD);
            assertThat(response.weekly().missions())
                    .extracting(MissionResponseDto::code)
                    .containsExactly(WEEKLY_ATTEND_5, WEEKLY_NOTE_10, WEEKLY_REVIEW_30, WEEKLY_SET_3);
        }

        @Test
        @DisplayName("미션마다 자기 기간 키가 실린다")
        void carriesPeriodKeyOnEveryMission() {
            MissionListResponseDto response = missionService.getMissions(user.getId());

            assertThat(response.daily().missions())
                    .extracting(MissionResponseDto::periodKey)
                    .containsOnly(MissionPeriodKey.daily(MissionPeriodKey.today()));
            assertThat(response.weekly().missions())
                    .extracting(MissionResponseDto::periodKey)
                    .containsOnly(MissionPeriodKey.weekly(MissionPeriodKey.today()));
        }

        @Test
        @DisplayName("진행도 행이 없는 미션에도 현재 기간 키가 실린다")
        void carriesCurrentPeriodKeyWhenProgressIsMissing() {
            MissionListResponseDto response = missionService.getMissions(user.getId());

            MissionResponseDto untouched = findByCode(response.weekly().missions(), WEEKLY_NOTE_10);
            assertThat(untouched.progressId()).isNull();
            assertThat(untouched.periodKey())
                    .as("진행도가 없어도 어느 기간의 미션인지는 알 수 있어야 한다")
                    .isEqualTo(MissionPeriodKey.weekly(MissionPeriodKey.today()));
        }

        @Test
        @DisplayName("아직 손대지 않은 미션도 0 으로 내려간다")
        void showsUntouchedMissionsAsZero() {
            MissionListResponseDto response = missionService.getMissions(user.getId());

            MissionResponseDto noteWrite = findByCode(response.daily().missions(), DAILY_NOTE_WRITE);
            assertThat(noteWrite.current()).isZero();
            assertThat(noteWrite.target()).isEqualTo(1);
            assertThat(noteWrite.completed()).isFalse();
            assertThat(noteWrite.claimed()).isFalse();
            assertThat(noteWrite.progressId()).isNull();
        }

        @Test
        @DisplayName("조회만으로는 진행도 행이 생기지 않는다")
        void doesNotCreateRowsOnRead() {
            missionService.getMissions(user.getId());

            assertThat(missionProgressRepository.findAll()).isEmpty();
        }

        @Test
        @DisplayName("완료된 미션에는 받기에 쓸 progressId 가 있다")
        void completedMissionCarriesProgressId() {
            completeMission(user.getId(), DAILY_NOTE_WRITE);

            MissionResponseDto noteWrite = findByCode(
                    missionService.getMissions(user.getId()).daily().missions(), DAILY_NOTE_WRITE);

            assertThat(noteWrite.completed()).isTrue();
            assertThat(noteWrite.progressId()).isNotNull();
            assertThat(noteWrite.rewardType()).isEqualTo(MissionRewardType.XP);
            assertThat(noteWrite.rewardValue()).isEqualTo(10);
        }

        @Test
        @DisplayName("받은 미션은 claimed 가 참으로 내려간다")
        void showsClaimedAsTrue() {
            // 프론트가 이 값으로 받기 버튼을 비활성화한다. 영영 false 로 나가도 화면은 조용히 잘못 동작한다.
            MissionProgress progress = completeMission(user.getId(), DAILY_NOTE_WRITE);
            missionService.claim(user.getId(), progress.getId());

            MissionResponseDto noteWrite = findByCode(
                    missionService.getMissions(user.getId()).daily().missions(), DAILY_NOTE_WRITE);

            assertThat(noteWrite.claimed()).isTrue();
            assertThat(noteWrite.completed()).isTrue();
        }

        @Test
        @DisplayName("진행 중에 목표가 바뀌어도 그 사람은 옛 목표로 끝낸다")
        void keepsTargetSnapshot() {
            // 진행도 행을 만들 때 target 을 박아 두는 이유다. 현재 정의를 읽으면
            // 운영 중에 목표를 낮췄을 때 이미 채운 사람 화면이 "5 / 3" 이 된다.
            missionProgressUpdater.increase(user.getId(), MissionMetric.SOLVE_RECORDED, 3);
            changeTarget(DAILY_REVIEW_3, 99);

            MissionResponseDto review = findByCode(
                    missionService.getMissions(user.getId()).daily().missions(), DAILY_REVIEW_3);

            assertThat(review.target())
                    .as("진행도가 있는 미션은 그 행이 만들어질 때의 목표를 쓴다")
                    .isEqualTo(3);
            assertThat(review.current()).isEqualTo(3);
            assertThat(review.completed()).isTrue();
        }

        @Test
        @DisplayName("진행도가 없는 미션은 바뀐 목표를 그대로 보여준다")
        void usesCurrentTargetWhenNoProgress() {
            changeTarget(DAILY_REVIEW_3, 99);

            MissionResponseDto review = findByCode(
                    missionService.getMissions(user.getId()).daily().missions(), DAILY_REVIEW_3);

            assertThat(review.target()).isEqualTo(99);
        }

        @Test
        @DisplayName("남의 진행도는 내 목록에 섞이지 않는다")
        void doesNotLeakOtherUsersProgress() {
            User other = fixtures.createOtherUser();
            completeMission(other.getId(), DAILY_NOTE_WRITE);

            MissionResponseDto noteWrite = findByCode(
                    missionService.getMissions(user.getId()).daily().missions(), DAILY_NOTE_WRITE);

            assertThat(noteWrite.current()).isZero();
            assertThat(noteWrite.progressId()).isNull();
        }

        private MissionResponseDto findByCode(List<MissionResponseDto> missions, String code) {
            return missions.stream()
                    .filter(mission -> mission.code().equals(code))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("목록에 없다: " + code));
        }
    }

    @Nested
    @DisplayName("지난 기간 미수령 보상")
    class ExpiredMissions {

        @Test
        @DisplayName("지난 주에 완료하고 안 받은 보상이 expired 로 내려온다")
        void showsUnclaimedFromPastPeriod() {
            Long progressId = insertCompletedProgress(
                    user.getId(), WEEKLY_REVIEW_30, lastWeekKey(), LocalDateTime.now().minusDays(1));

            MissionListResponseDto response = missionService.getMissions(user.getId());

            assertThat(response.expired().missions())
                    .as("복습 30회를 채우고 못 받은 보상이 사라지면 그대로 CS 다")
                    .hasSize(1);
            MissionResponseDto expired = response.expired().missions().get(0);
            assertThat(expired.code()).isEqualTo(WEEKLY_REVIEW_30);
            assertThat(expired.progressId()).isEqualTo(progressId);
            assertThat(expired.completed()).isTrue();
            assertThat(expired.claimed()).isFalse();
            assertThat(expired.periodKey())
                    .as("기간 키는 그 진행도의 원래 키를 그대로 준다")
                    .isEqualTo(lastWeekKey());
        }

        @Test
        @DisplayName("expired 묶음에는 여러 기간이 섞이므로 묶음 기간 키가 없다")
        void expiredSectionHasNoSinglePeriodKey() {
            insertCompletedProgress(user.getId(), WEEKLY_REVIEW_30, lastWeekKey(), LocalDateTime.now().minusDays(1));
            insertCompletedProgress(user.getId(), DAILY_REVIEW_3, yesterdayKey(), LocalDateTime.now().minusDays(1));

            MissionListResponseDto response = missionService.getMissions(user.getId());

            assertThat(response.expired().periodKey()).isNull();
            assertThat(response.expired().missions())
                    .extracting(MissionResponseDto::periodKey)
                    .containsExactlyInAnyOrder(lastWeekKey(), yesterdayKey());
        }

        @Test
        @DisplayName("일일과 주간이 섞여도 최근에 놓친 것이 위로 온다")
        void sortsByCompletedAtNotByPeriodKeyString() {
            // 기간 키 문자열로 정렬하면 "2026-W37" 과 "2026-09-09" 가 여섯 번째 글자에서 갈려
            // 주간이 언제나 일일보다 앞선다. 3주 전 주간 미션이 어제 놓친 일일 미션보다 위로 올라간다.
            insertCompletedProgress(
                    user.getId(), WEEKLY_REVIEW_30, lastWeekKey(), LocalDateTime.now().minusDays(20));
            insertCompletedProgress(
                    user.getId(), DAILY_REVIEW_3, yesterdayKey(), LocalDateTime.now().minusDays(1));

            MissionListResponseDto response = missionService.getMissions(user.getId());

            assertThat(response.expired().missions())
                    .extracting(MissionResponseDto::code)
                    .as("어제 놓친 일일 미션이 3주 전 주간 미션보다 위에 있어야 한다")
                    .containsExactly(DAILY_REVIEW_3, WEEKLY_REVIEW_30);
        }

        @Test
        @DisplayName("기간이 지나도 받을 수 있다")
        void canBeClaimed() {
            Long progressId = insertCompletedProgress(
                    user.getId(), WEEKLY_REVIEW_30, lastWeekKey(), LocalDateTime.now().minusDays(1));

            MissionClaimResponseDto response = missionService.claim(user.getId(), progressId);

            assertThat(response.rewardValue()).isEqualTo(100);
            assertThat(missionService.getMissions(user.getId()).expired().missions())
                    .as("받고 나면 목록에서 빠진다")
                    .isEmpty();
        }

        @Test
        @DisplayName("이번 기간의 완료 미수령은 expired 가 아니다")
        void currentPeriodIsNotExpired() {
            completeMission(user.getId(), DAILY_NOTE_WRITE);

            MissionListResponseDto response = missionService.getMissions(user.getId());

            assertThat(response.expired().missions()).isEmpty();
        }

        @Test
        @DisplayName("완료하지 않은 지난 기간 진행도는 내려가지 않는다")
        void ignoresIncompletePastProgress() {
            jdbcTemplate.update("""
                    INSERT INTO mission_progress
                        (user_id, mission_id, period_key, current_value, target_snapshot, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, NOW(6), NOW(6))
                    """,
                    user.getId(), definitionOf(WEEKLY_REVIEW_30).getId(), lastWeekKey(), 12, 30);

            assertThat(missionService.getMissions(user.getId()).expired().missions()).isEmpty();
        }

        @Test
        @DisplayName("조회 상한보다 오래된 것은 내려가지 않는다")
        void ignoresTooOldProgress() {
            insertCompletedProgress(
                    user.getId(), WEEKLY_REVIEW_30, "2020-W10", LocalDateTime.now().minusDays(40));

            assertThat(missionService.getMissions(user.getId()).expired().missions())
                    .as("상한이 없으면 몇 년치 미수령이 매 조회마다 딸려 온다")
                    .isEmpty();
        }

        @Test
        @DisplayName("상한 경계는 30일이다 - 29일 반나절 전은 들어오고 30일 한 시간 전은 빠진다")
        void lookbackBoundaryIsThirtyDays() {
            // 두 점이 상한을 30 으로 못박는다. 29.5일 전이 들어오려면 상한이 30 이상이어야 하고,
            // 30일 1시간 전이 빠지려면 30 이하여야 한다. 20일과 40일만 재면 29 로 바꿔도 통과한다.
            insertCompletedProgress(user.getId(), WEEKLY_REVIEW_30, "2020-W10",
                    LocalDateTime.now().minusDays(29).minusHours(12));
            insertCompletedProgress(user.getId(), WEEKLY_NOTE_10, "2020-W11",
                    LocalDateTime.now().minusDays(30).minusHours(1));

            assertThat(missionService.getMissions(user.getId()).expired().missions())
                    .extracting(MissionResponseDto::code)
                    .containsExactly(WEEKLY_REVIEW_30);
        }

        @Test
        @DisplayName("정의가 비활성화돼도 이미 완료한 미수령 보상은 내려간다")
        void keepsDeactivatedDefinition() {
            insertCompletedProgress(
                    user.getId(), WEEKLY_REVIEW_30, lastWeekKey(), LocalDateTime.now().minusDays(1));
            deactivateDefinition(WEEKLY_REVIEW_30);

            assertThat(missionService.getMissions(user.getId()).expired().missions())
                    .as("이미 완료한 건 받을 수 있어야 한다")
                    .extracting(MissionResponseDto::code)
                    .containsExactly(WEEKLY_REVIEW_30);
        }

        @Test
        @DisplayName("남의 미수령 보상은 내 목록에 섞이지 않는다")
        void doesNotLeakOtherUsers() {
            User other = fixtures.createOtherUser();
            insertCompletedProgress(other.getId(), WEEKLY_REVIEW_30, lastWeekKey(), LocalDateTime.now().minusDays(1));

            assertThat(missionService.getMissions(user.getId()).expired().missions()).isEmpty();
        }
    }

    @Nested
    @DisplayName("보상 받기")
    class Claim {

        @Test
        @DisplayName("완료한 미션의 보상을 받으면 XP 가 들어온다")
        void grantsXp() {
            MissionProgress progress = completeMission(user.getId(), DAILY_REVIEW_3);
            long before = problemPracticePoints();

            MissionClaimResponseDto response = missionService.claim(user.getId(), progress.getId());

            assertThat(response.progressId()).isEqualTo(progress.getId());
            assertThat(response.rewardType()).isEqualTo(MissionRewardType.XP);
            assertThat(response.rewardValue()).isEqualTo(15);
            assertThat(response.totalStudyLevel()).isNotNull();
            assertThat(problemPracticePoints() - before)
                    .as("보상은 metric 이 가리키는 능력치에 들어간다")
                    .isEqualTo(15);
        }

        @Test
        @DisplayName("받고 나면 목록에 claimed 로 보인다")
        void marksClaimed() {
            MissionProgress progress = completeMission(user.getId(), DAILY_REVIEW_3);

            missionService.claim(user.getId(), progress.getId());

            assertThat(missionProgressRepository.findById(progress.getId()).orElseThrow().getClaimedAt())
                    .isNotNull();
        }

        @Test
        @DisplayName("아직 완료하지 않은 미션은 받을 수 없다")
        void rejectsIncompleteMission() {
            missionProgressUpdater.increase(user.getId(), MissionMetric.SOLVE_RECORDED, 2);
            MissionProgress progress = progressOf(user, DAILY_REVIEW_3);

            assertThatThrownBy(() -> missionService.claim(user.getId(), progress.getId()))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(exception -> ((ApplicationException) exception).getErrorCase())
                    .isEqualTo(MissionErrorCase.MISSION_NOT_COMPLETED);
        }

        @Test
        @DisplayName("두 번 받을 수 없다")
        void rejectsSecondClaim() {
            MissionProgress progress = completeMission(user.getId(), DAILY_REVIEW_3);
            missionService.claim(user.getId(), progress.getId());
            long afterFirstClaim = problemPracticePoints();

            assertThatThrownBy(() -> missionService.claim(user.getId(), progress.getId()))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(exception -> ((ApplicationException) exception).getErrorCase())
                    .isEqualTo(MissionErrorCase.MISSION_ALREADY_CLAIMED);
            assertThat(problemPracticePoints()).isEqualTo(afterFirstClaim);
        }

        @Test
        @DisplayName("없는 진행도는 받을 수 없다")
        void rejectsUnknownProgress() {
            MissionProgress progress = completeMission(user.getId(), DAILY_REVIEW_3);

            assertThatThrownBy(() -> missionService.claim(user.getId(), progress.getId() + 1_000L))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(exception -> ((ApplicationException) exception).getErrorCase())
                    .isEqualTo(MissionErrorCase.MISSION_PROGRESS_NOT_FOUND);
        }

        @Test
        @DisplayName("남의 미션은 받을 수 없다 - 있는지 없는지도 알려주지 않는다")
        void rejectsOtherUsersProgress() {
            MissionProgress progress = completeMission(user.getId(), DAILY_REVIEW_3);
            User other = fixtures.createOtherUser();

            assertThatThrownBy(() -> missionService.claim(other.getId(), progress.getId()))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(exception -> ((ApplicationException) exception).getErrorCase())
                    .isEqualTo(MissionErrorCase.MISSION_PROGRESS_NOT_FOUND);
            assertThat(missionProgressRepository.findById(progress.getId()).orElseThrow().getClaimedAt())
                    .as("거절됐으면 수령 도장도 찍히면 안 된다")
                    .isNull();
        }

        @Test
        @DisplayName("정의가 사라진 진행도는 받을 수 없다")
        void rejectsWhenDefinitionIsGone() {
            MissionProgress progress = completeMission(user.getId(), DAILY_REVIEW_3);
            jdbcTemplate.update("DELETE FROM mission_definition WHERE id = ?", progress.getMissionId());

            assertThatThrownBy(() -> missionService.claim(user.getId(), progress.getId()))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(exception -> ((ApplicationException) exception).getErrorCase())
                    .isEqualTo(MissionErrorCase.MISSION_PROGRESS_NOT_FOUND);
            assertThat(missionProgressRepository.findById(progress.getId()).orElseThrow().getClaimedAt())
                    .as("보상 값을 알 수 없으니 수령 도장도 찍히면 안 된다")
                    .isNull();
        }

        @Test
        @DisplayName("사용자가 사라졌으면 USER_NOT_FOUND 로 거절한다")
        void rejectsWhenUserIsGone() {
            MissionProgress progress = completeMission(user.getId(), DAILY_REVIEW_3);
            jdbcTemplate.update("UPDATE user SET deleted_at = NOW(6) WHERE id = ?", user.getId());

            assertThatThrownBy(() -> missionService.claim(user.getId(), progress.getId()))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(exception -> ((ApplicationException) exception).getErrorCase())
                    .isEqualTo(MissionErrorCase.USER_NOT_FOUND);
            assertThat(missionProgressRepository.findById(progress.getId()).orElseThrow().getClaimedAt())
                    .as("지급할 대상이 없으면 수령 도장도 찍히면 안 된다")
                    .isNull();
        }

        @Test
        @DisplayName("행동 기록이 아무리 쌓여 있어도 XP 는 받을 때만 들어온다")
        void grantsOnlyOnClaim() {
            // 예전에는 mission_log 가 쌓일 때마다 자동으로 XP 가 들어가고 하루 200점 상한이 그것만 눌렀다.
            // 이제 지급 경로는 받기 하나뿐이라, 기록이 얼마나 있든 받기 전에는 0 이다.
            saveMissionLog(user, MissionType.PROBLEM_WRITE, null);
            jdbcTemplate.update("UPDATE mission_log SET point = 300 WHERE user_id = ?", user.getId());

            MissionProgress progress = completeMission(user.getId(), DAILY_REVIEW_3);
            assertThat(problemPracticePoints())
                    .as("완료만 해서는 오르지 않는다")
                    .isZero();

            missionService.claim(user.getId(), progress.getId());

            assertThat(problemPracticePoints()).isEqualTo(15);
        }

        @Test
        @DisplayName("보상으로 레벨이 오르면 leveledUp 이 참이다")
        void reportsLevelUp() {
            // 총 학습 레벨 1→2 에 필요한 경험치는 40 이다. 주간 복습 미션 보상 100 이면 반드시 오른다.
            MissionProgress progress = completeMission(user.getId(), WEEKLY_REVIEW_30);

            MissionClaimResponseDto response = missionService.claim(user.getId(), progress.getId());

            assertThat(response.leveledUp()).isTrue();
            assertThat(response.totalStudyLevel()).isGreaterThan(1L);
        }

        private long problemPracticePoints() {
            UserMissionStatus status = reload(user).getUserMissionStatus();
            return accumulatedPoints(status.getProblemPracticeLevel(), status.getProblemPracticePoint());
        }
    }
}
