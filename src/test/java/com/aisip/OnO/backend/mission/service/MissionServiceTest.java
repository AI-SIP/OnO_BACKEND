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
        @DisplayName("하루 200점 상한을 채운 뒤에도 미션 보상은 들어온다")
        void ignoresDailyPointCap() {
            // 상한은 기존 적립(MissionLog) 경로에만 있는 규칙이다. 미션 보상은 그 위에 얹는 보너스라 상한 밖이다.
            saveMissionLog(user, MissionType.PROBLEM_WRITE, null);
            jdbcTemplate.update("UPDATE mission_log SET point = 300 WHERE user_id = ?", user.getId());

            MissionProgress progress = completeMission(user.getId(), DAILY_REVIEW_3);
            long before = problemPracticePoints();

            missionService.claim(user.getId(), progress.getId());

            assertThat(problemPracticePoints() - before).isEqualTo(15);
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
