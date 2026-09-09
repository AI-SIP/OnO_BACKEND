package com.aisip.OnO.backend.mission.repository;

import com.aisip.OnO.backend.mission.entity.MissionProgress;
import com.aisip.OnO.backend.mission.support.MissionSystemTestSupport;
import com.aisip.OnO.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 소유권 검증은 서비스가 아니라 쿼리에 박혀 있어야 한다.
 *
 * <p>서비스에만 두면 이 메서드를 다른 곳에서 부르는 순간 검증이 통째로 빠진다.
 * 여기서는 리포지토리를 직접 불러 그 방어가 살아 있는지만 본다.
 */
@DisplayName("미션 진행도 리포지토리 - 소유권")
class MissionProgressRepositoryTest extends MissionSystemTestSupport {

    private User user;

    @BeforeEach
    void setUpUser() {
        user = fixtures.createUser();
    }

    @Test
    @DisplayName("남의 진행도는 수령 도장이 찍히지 않는다")
    void markClaimedRejectsOtherUser() {
        MissionProgress progress = completeMission(user.getId(), DAILY_NOTE_WRITE);
        User other = fixtures.createOtherUser();

        Integer updated = claim(progress.getId(), other.getId());

        assertThat(updated).isZero();
        assertThat(missionProgressRepository.findById(progress.getId()).orElseThrow().getClaimedAt()).isNull();
    }

    /** {@code @Modifying} 쿼리는 flush 를 하므로 트랜잭션 안에서 불러야 한다. */
    private Integer claim(Long progressId, Long userId) {
        return transactionTemplate.execute(
                status -> missionProgressRepository.markClaimed(progressId, userId));
    }

    @Test
    @DisplayName("주인이면 한 번만 찍힌다")
    void markClaimedStampsOnce() {
        MissionProgress progress = completeMission(user.getId(), DAILY_NOTE_WRITE);

        Integer first = claim(progress.getId(), user.getId());
        Integer second = claim(progress.getId(), user.getId());

        assertThat(first).isEqualTo(1);
        assertThat(second)
                .as("이미 받았으면 갱신된 행이 없어야 두 번 지급을 막을 수 있다")
                .isZero();
    }
}
