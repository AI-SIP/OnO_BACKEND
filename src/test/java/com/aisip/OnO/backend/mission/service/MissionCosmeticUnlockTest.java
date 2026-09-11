package com.aisip.OnO.backend.mission.service;

import com.aisip.OnO.backend.cosmetic.dto.UnlockedCosmeticDto;
import com.aisip.OnO.backend.cosmetic.entity.CosmeticSlot;
import com.aisip.OnO.backend.cosmetic.service.CosmeticService;
import com.aisip.OnO.backend.cosmetic.support.CosmeticItemSeeder;
import com.aisip.OnO.backend.mission.dto.MissionClaimResponseDto;
import com.aisip.OnO.backend.mission.entity.MissionProgress;
import com.aisip.OnO.backend.mission.support.MissionSystemTestSupport;
import com.aisip.OnO.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 미션 보상을 받아 레벨이 오르면 그 자리에서 무엇이 열렸는지 알려 준다.
 *
 * <p>해금 자체는 계산이라 알림이 없어도 다음 조회에서 드러나지만, 사용자가 "레벨이 올랐는데
 * 뭐가 좋아졌는지" 를 그 순간에 모르면 레벨업이 아무 일도 아닌 것이 된다.
 *
 * <p>{@code MissionRewardGranter} 의 트랜잭션 경계와 잠금 순서(사용자 → 진행도)는 건드리지 않았다.
 * 해금 조회는 지급이 끝난 뒤에 도는 읽기 전용 조회다.
 */
@DisplayName("미션 보상 - 레벨업 해금 알림")
class MissionCosmeticUnlockTest extends MissionSystemTestSupport {

    @Autowired
    private CosmeticItemSeeder cosmeticItemSeeder;

    @Autowired
    private CosmeticService cosmeticService;

    private User user;

    @BeforeEach
    void setUpUserAndCatalog() {
        // 미션 도메인 베이스라 꾸미기 카탈로그는 여기서 따로 채운다.
        cosmeticItemSeeder.seed();
        user = fixtures.createUser();
    }

    @Test
    @DisplayName("레벨이 안 올랐으면 빈 배열이다")
    void noUnlockWithoutLevelUp() {
        // 오늘 기분 미션은 5점이라 레벨 1→2 에 필요한 40점에 못 미친다.
        MissionProgress progress = completeMission(user.getId(), DAILY_MOOD);

        MissionClaimResponseDto response = missionService.claim(user.getId(), progress.getId());

        assertThat(response.leveledUp()).isFalse();
        assertThat(response.unlockedCosmetics())
                .as("null 이 아니라 빈 배열이어야 프론트가 null 검사를 안 한다")
                .isNotNull()
                .isEmpty();
    }

    @Test
    @DisplayName("레벨이 오르면 그 구간에서 열린 아이템이 실린다")
    void unlocksOnLevelUp() {
        // 레벨 1→2 에 40점이 필요하다. 주간 출석(100점) 하나면 넘는다.
        MissionProgress progress = completeMission(user.getId(), WEEKLY_ATTEND_5);

        MissionClaimResponseDto response = missionService.claim(user.getId(), progress.getId());

        assertThat(response.leveledUp()).isTrue();
        assertThat(response.totalStudyLevel()).isEqualTo(2L);
        assertThat(response.unlockedCosmetics())
                .extracting(UnlockedCosmeticDto::itemKey)
                .as("레벨 2 에 열리는 것은 새싹 머리띠다")
                .containsExactly("headband_sprout");
        assertThat(response.unlockedCosmetics().get(0).slot()).isEqualTo(CosmeticSlot.HEAD);
        assertThat(response.unlockedCosmetics().get(0).nameKo()).isEqualTo("새싹 머리띠");
    }

    @Test
    @DisplayName("한 번에 여러 단계 오르면 그 사이 것이 전부 실린다")
    void unlocksEveryLevelInRange() {
        // 레벨 1→2 는 40, 2→3 은 80 이다. 주간 4종(100+80+100+80=360)을 몰아 받으면 여러 단계가 오른다.
        for (String code : new String[]{WEEKLY_ATTEND_5, WEEKLY_NOTE_10, WEEKLY_REVIEW_30, WEEKLY_SET_3}) {
            completeMission(user.getId(), code);
        }

        MissionClaimResponseDto last = null;
        for (String code : new String[]{WEEKLY_ATTEND_5, WEEKLY_NOTE_10, WEEKLY_REVIEW_30, WEEKLY_SET_3}) {
            last = missionService.claim(user.getId(), progressOf(user, code).getId());
        }

        assertThat(last).isNotNull();
        assertThat(last.totalStudyLevel()).isGreaterThan(2L);
        // 마지막 수령까지의 해금을 다 합치면 현재 레벨까지 열린 것이 빠짐없이 한 번씩 나왔어야 한다.
        assertThat(cosmeticService.findUnlockedBetween(1, last.totalStudyLevel()))
                .extracting(UnlockedCosmeticDto::itemKey)
                .contains("headband_sprout", "bg_spring");
    }

    @Test
    @DisplayName("기존 응답 필드는 그대로다 - 구버전 앱이 깨지면 안 된다")
    void keepsExistingFields() {
        MissionProgress progress = completeMission(user.getId(), DAILY_ATTEND);

        MissionClaimResponseDto response = missionService.claim(user.getId(), progress.getId());

        assertThat(response.progressId()).isEqualTo(progress.getId());
        assertThat(response.rewardType()).isEqualTo(definitionOf(DAILY_ATTEND).getRewardType());
        assertThat(response.rewardValue()).isEqualTo(definitionOf(DAILY_ATTEND).getRewardValue());
        assertThat(response.totalStudyLevel()).isNotNull();
    }
}
