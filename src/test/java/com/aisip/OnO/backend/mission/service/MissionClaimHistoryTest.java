package com.aisip.OnO.backend.mission.service;

import com.aisip.OnO.backend.mission.dto.MissionClaimHistoryItemDto;
import com.aisip.OnO.backend.mission.dto.MissionClaimHistoryResponseDto;
import com.aisip.OnO.backend.mission.entity.MissionProgress;
import com.aisip.OnO.backend.mission.support.MissionSystemTestSupport;
import com.aisip.OnO.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 보상 획득 기록 조회.
 *
 * <p>새 테이블 없이 {@code mission_progress.claimed_at} 을 그대로 읽는다.
 * 정렬이 id 가 아니라 받은 시각이라, 커서 페이지네이션이 경계에서 빠지거나 겹치지 않는지가 핵심이다.
 */
@DisplayName("미션 보상 획득 기록")
class MissionClaimHistoryTest extends MissionSystemTestSupport {

    /** 기간 키 리터럴은 "지금"과 겹치면 유니크 제약에 걸린다. 절대 현재가 될 수 없는 과거로 못박는다. */
    private static final LocalDateTime BASE = LocalDateTime.of(2020, 9, 1, 10, 0);

    private User user;

    @BeforeEach
    void setUpUser() {
        user = fixtures.createUser();
    }

    @Nested
    @DisplayName("무엇이 나오는가")
    class Contents {

        @Test
        @DisplayName("받은 것만 나오고 안 받은 것은 나오지 않는다")
        void showsOnlyClaimed() {
            insertClaimedProgress(user.getId(), WEEKLY_NOTE_10, "2020-W36", BASE);
            completeMission(user.getId(), DAILY_NOTE_WRITE);

            MissionClaimHistoryResponseDto history = missionService.getClaimHistory(user.getId(), null, 20);

            assertThat(history.content())
                    .extracting(MissionClaimHistoryItemDto::code)
                    .containsExactly(WEEKLY_NOTE_10);
        }

        @Test
        @DisplayName("항목에 미션 정보와 받은 시각이 실린다")
        void carriesMissionDetail() {
            Long progressId = insertClaimedProgress(user.getId(), WEEKLY_NOTE_10, "2020-W36", BASE);

            MissionClaimHistoryItemDto item =
                    missionService.getClaimHistory(user.getId(), null, 20).content().get(0);

            assertThat(item.progressId()).isEqualTo(progressId);
            assertThat(item.code()).isEqualTo(WEEKLY_NOTE_10);
            assertThat(item.title()).isEqualTo("열 권의 노트");
            assertThat(item.iconKey()).isEqualTo("note_write");
            assertThat(item.category().name()).isEqualTo("WEEKLY");
            assertThat(item.periodKey()).isEqualTo("2020-W36");
            assertThat(item.rewardType().name()).isEqualTo("XP");
            assertThat(item.rewardValue()).isEqualTo(80);
            assertThat(item.claimedAt()).isEqualTo(BASE);
        }

        @Test
        @DisplayName("실제로 받으면 그대로 기록에 남는다")
        void recordsRealClaim() {
            MissionProgress progress = completeMission(user.getId(), DAILY_NOTE_WRITE);
            missionService.claim(user.getId(), progress.getId());

            MissionClaimHistoryResponseDto history = missionService.getClaimHistory(user.getId(), null, 20);

            assertThat(history.content()).singleElement().satisfies(item -> {
                assertThat(item.progressId()).isEqualTo(progress.getId());
                assertThat(item.claimedAt()).isNotNull();
            });
        }

        @Test
        @DisplayName("받은 시각 내림차순으로 준다")
        void sortsByClaimedAtDescending() {
            insertClaimedProgress(user.getId(), DAILY_NOTE_WRITE, "2020-09-01", BASE);
            insertClaimedProgress(user.getId(), DAILY_REVIEW_3, "2020-09-02", BASE.plusDays(2));
            insertClaimedProgress(user.getId(), DAILY_MOOD, "2020-09-03", BASE.plusDays(1));

            MissionClaimHistoryResponseDto history = missionService.getClaimHistory(user.getId(), null, 20);

            assertThat(history.content())
                    .extracting(MissionClaimHistoryItemDto::claimedAt)
                    .containsExactly(BASE.plusDays(2), BASE.plusDays(1), BASE);
        }

        @Test
        @DisplayName("정의가 비활성화된 미션도 기록에는 남는다")
        void keepsDeactivatedMission() {
            insertClaimedProgress(user.getId(), WEEKLY_NOTE_10, "2020-W36", BASE);
            deactivateDefinition(WEEKLY_NOTE_10);

            MissionClaimHistoryResponseDto history = missionService.getClaimHistory(user.getId(), null, 20);

            assertThat(history.content())
                    .as("이미 받은 건 받은 것이다")
                    .extracting(MissionClaimHistoryItemDto::code)
                    .containsExactly(WEEKLY_NOTE_10);
        }

        @Test
        @DisplayName("받은 게 없으면 빈 목록과 합계 0")
        void returnsEmptyWhenNothingClaimed() {
            MissionClaimHistoryResponseDto history = missionService.getClaimHistory(user.getId(), null, 20);

            assertThat(history.content()).isEmpty();
            assertThat(history.hasNext()).isFalse();
            assertThat(history.nextCursor()).isNull();
            assertThat(history.totalClaimedXp()).isZero();
            assertThat(history.totalClaimedCount()).isZero();
        }
    }

    @Nested
    @DisplayName("보상 스냅샷")
    class RewardSnapshot {

        @Test
        @DisplayName("받은 뒤 보상을 바꿔도 기록의 값은 그대로다")
        void keepsRewardValueAtClaimTime() {
            MissionProgress progress = completeMission(user.getId(), DAILY_NOTE_WRITE);
            missionService.claim(user.getId(), progress.getId());

            changeRewardValue(DAILY_NOTE_WRITE, 999);

            assertThat(missionService.getClaimHistory(user.getId(), null, 20).content())
                    .singleElement()
                    .satisfies(item -> assertThat(item.rewardValue())
                            .as("10 XP 를 받은 기록이 어느 날 999 XP 로 바뀌면 안 된다")
                            .isEqualTo(10));
        }

        @Test
        @DisplayName("합계도 받은 시점의 값으로 센다")
        void sumsRewardValueAtClaimTime() {
            MissionProgress progress = completeMission(user.getId(), DAILY_NOTE_WRITE);
            missionService.claim(user.getId(), progress.getId());

            changeRewardValue(DAILY_NOTE_WRITE, 999);

            assertThat(missionService.getClaimHistory(user.getId(), null, 20).totalClaimedXp())
                    .as("목록에 보이는 값과 합계가 어긋나면 안 된다")
                    .isEqualTo(10L);
        }

        @Test
        @DisplayName("스냅샷이 없는 옛 행은 현재 정의 값으로 보인다")
        void fallsBackToCurrentDefinition() {
            insertClaimedProgressWithoutSnapshot(user.getId(), WEEKLY_NOTE_10, "2020-W36", BASE);

            assertThat(missionService.getClaimHistory(user.getId(), null, 20).content())
                    .singleElement()
                    .satisfies(item -> {
                        assertThat(item.rewardValue()).isEqualTo(80);
                        assertThat(item.rewardType().name()).isEqualTo("XP");
                    });
            assertThat(missionService.getClaimHistory(user.getId(), null, 20).totalClaimedXp())
                    .isEqualTo(80L);
        }

        @Test
        @DisplayName("받는 순간 스냅샷이 실제로 박힌다")
        void stampsSnapshotOnClaim() {
            MissionProgress progress = completeMission(user.getId(), WEEKLY_REVIEW_30);

            missionService.claim(user.getId(), progress.getId());

            MissionProgress claimed = missionProgressRepository.findById(progress.getId()).orElseThrow();
            assertThat(claimed.getClaimedAt())
                    .as("스냅샷을 쓰면서 수령 시각을 덮어쓰면 안 된다")
                    .isNotNull();
            assertThat(claimed.getRewardTypeSnapshot().name()).isEqualTo("XP");
            assertThat(claimed.getRewardValueSnapshot()).isEqualTo(100);
        }
    }

    @Nested
    @DisplayName("소유권")
    class Ownership {

        @Test
        @DisplayName("남의 기록은 나오지 않는다")
        void doesNotLeakOtherUsers() {
            User other = fixtures.createOtherUser();
            insertClaimedProgress(other.getId(), WEEKLY_NOTE_10, "2020-W36", BASE);
            insertClaimedProgress(other.getId(), DAILY_MOOD, "2020-09-01", BASE.plusDays(1));

            MissionClaimHistoryResponseDto history = missionService.getClaimHistory(user.getId(), null, 20);

            assertThat(history.content()).isEmpty();
            assertThat(history.totalClaimedXp()).isZero();
            assertThat(history.totalClaimedCount()).isZero();
        }

        @Test
        @DisplayName("아직 안 받은 내 진행도를 커서로 넘기면 빈 페이지를 준다")
        void rejectsUnclaimedCursor() {
            // 커서 행에 claimed_at 이 없으면 자를 기준이 없다. 400 을 던지는 것보다 빈 페이지가 안전하다.
            MissionProgress unclaimed = completeMission(user.getId(), DAILY_NOTE_WRITE);
            insertClaimedProgress(user.getId(), DAILY_MOOD, "2020-09-01", BASE);

            MissionClaimHistoryResponseDto history =
                    missionService.getClaimHistory(user.getId(), unclaimed.getId(), 20);

            assertThat(history.content()).isEmpty();
            assertThat(history.hasNext()).isFalse();
        }

        @Test
        @DisplayName("남의 진행도를 커서로 넘겨도 남의 기록이 새지 않는다")
        void rejectsOtherUsersCursor() {
            User other = fixtures.createOtherUser();
            Long othersProgressId = insertClaimedProgress(other.getId(), WEEKLY_NOTE_10, "2020-W36", BASE);
            insertClaimedProgress(user.getId(), DAILY_MOOD, "2020-09-01", BASE.plusDays(1));

            MissionClaimHistoryResponseDto history =
                    missionService.getClaimHistory(user.getId(), othersProgressId, 20);

            assertThat(history.content()).isEmpty();
        }
    }

    @Nested
    @DisplayName("커서 페이지네이션")
    class Pagination {

        @Test
        @DisplayName("페이지를 이어 붙이면 빠지거나 겹치는 것 없이 전부 나온다")
        void walksEveryPageExactlyOnce() {
            for (int i = 0; i < 5; i++) {
                insertClaimedProgress(user.getId(), codeAt(i), "2020-09-0" + (i + 1), BASE.plusHours(i));
            }

            List<Long> visited = walkAllPages(2);

            assertThat(visited)
                    .as("5건을 2개씩 넘겨도 경계에서 빠지거나 겹치면 안 된다")
                    .hasSize(5)
                    .doesNotHaveDuplicates();
        }

        @Test
        @DisplayName("받은 시각이 같아도 경계에서 빠지거나 겹치지 않는다")
        void handlesTiesOnClaimedAt() {
            for (int i = 0; i < 4; i++) {
                insertClaimedProgress(user.getId(), codeAt(i), "2020-09-0" + (i + 1), BASE);
            }

            List<Long> visited = walkAllPages(2);

            assertThat(visited)
                    .as("시각이 같으면 id 가 순서를 갈라 줘야 한다")
                    .hasSize(4)
                    .doesNotHaveDuplicates();
        }

        @Test
        @DisplayName("마지막 페이지에서는 hasNext 가 거짓이고 커서가 없다")
        void marksLastPage() {
            insertClaimedProgress(user.getId(), DAILY_MOOD, "2020-09-01", BASE);
            insertClaimedProgress(user.getId(), DAILY_REVIEW_3, "2020-09-02", BASE.plusHours(1));

            MissionClaimHistoryResponseDto page = missionService.getClaimHistory(user.getId(), null, 2);

            assertThat(page.content()).hasSize(2);
            assertThat(page.hasNext()).isFalse();
            assertThat(page.nextCursor()).isNull();
        }

        @Test
        @DisplayName("size 는 상한을 넘지 않는다")
        void capsPageSize() {
            MissionClaimHistoryResponseDto history = missionService.getClaimHistory(user.getId(), null, 500);

            assertThat(history.size()).isEqualTo(50);
        }

        @Test
        @DisplayName("size 가 0 이거나 음수여도 터지지 않는다")
        void guardsNonPositivePageSize() {
            // 쿼리 파라미터라 밖에서 그대로 찔린다. 하한이 없으면 PageRequest.of(0, 0) 이 되어 500 이 난다.
            insertClaimedProgress(user.getId(), DAILY_MOOD, "2020-09-01", BASE);

            for (int size : new int[]{0, -1, Integer.MIN_VALUE}) {
                MissionClaimHistoryResponseDto history = missionService.getClaimHistory(user.getId(), null, size);

                assertThat(history.size()).as("size=%d", size).isEqualTo(1);
                assertThat(history.content()).as("size=%d", size).hasSize(1);
            }
        }

        private List<Long> walkAllPages(int size) {
            List<Long> visited = new ArrayList<>();
            Long cursor = null;
            for (int guard = 0; guard < 20; guard++) {
                MissionClaimHistoryResponseDto page = missionService.getClaimHistory(user.getId(), cursor, size);
                page.content().forEach(item -> visited.add(item.progressId()));
                if (!page.hasNext()) {
                    return visited;
                }
                cursor = page.nextCursor();
            }
            throw new IllegalStateException("페이지가 끝나지 않는다 - 커서가 앞으로 나아가지 않는다");
        }

        private String codeAt(int index) {
            return List.of(DAILY_ATTEND, DAILY_NOTE_WRITE, DAILY_REVIEW_3, DAILY_CORRECT_3, DAILY_MOOD).get(index);
        }
    }

    @Nested
    @DisplayName("합계")
    class Totals {

        @Test
        @DisplayName("첫 페이지에만 합계를 싣는다")
        void carriesTotalsOnFirstPageOnly() {
            insertClaimedProgress(user.getId(), DAILY_NOTE_WRITE, "2020-09-01", BASE);
            insertClaimedProgress(user.getId(), DAILY_REVIEW_3, "2020-09-02", BASE.plusHours(1));
            insertClaimedProgress(user.getId(), WEEKLY_NOTE_10, "2020-W36", BASE.plusHours(2));

            MissionClaimHistoryResponseDto first = missionService.getClaimHistory(user.getId(), null, 2);
            assertThat(first.totalClaimedXp())
                    .as("10 + 15 + 80")
                    .isEqualTo(105L);
            assertThat(first.totalClaimedCount()).isEqualTo(3L);

            MissionClaimHistoryResponseDto second =
                    missionService.getClaimHistory(user.getId(), first.nextCursor(), 2);
            assertThat(second.totalClaimedXp())
                    .as("페이지를 넘길 때마다 전체를 다시 세지 않는다")
                    .isNull();
            assertThat(second.totalClaimedCount()).isNull();
        }

        @Test
        @DisplayName("안 받은 미션은 합계에 들어가지 않는다")
        void excludesUnclaimed() {
            insertClaimedProgress(user.getId(), DAILY_NOTE_WRITE, "2020-09-01", BASE);
            completeMission(user.getId(), WEEKLY_REVIEW_30);

            MissionClaimHistoryResponseDto history = missionService.getClaimHistory(user.getId(), null, 20);

            assertThat(history.totalClaimedXp()).isEqualTo(10L);
            assertThat(history.totalClaimedCount()).isEqualTo(1L);
        }
    }
}
