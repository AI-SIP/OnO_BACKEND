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
 * <p>해금 기준이 능력치별로 갈리면서 여기가 특히 중요해졌다. 시드된 63 개 중 47 개가
 * 능력치로 열린다. 총 학습 레벨만 보면 보상을 받아도 알림이 거의 늘 비어 있게 된다.
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
    @DisplayName("아무 레벨도 안 올랐으면 빈 배열이다")
    void noUnlockWithoutLevelUp() {
        // 오늘 기분 미션은 5점이라 출석 레벨 1→2 에 필요한 10점에도 못 미친다.
        MissionProgress progress = completeMission(user.getId(), DAILY_MOOD);

        MissionClaimResponseDto response = missionService.claim(user.getId(), progress.getId());

        assertThat(response.leveledUp()).isFalse();
        assertThat(response.unlockedCosmetics())
                .as("null 이 아니라 빈 배열이어야 프론트가 null 검사를 안 한다")
                .isNotNull()
                .isEmpty();
    }

    @Test
    @DisplayName("총 학습 레벨이 그대로여도 능력치가 오르면 해금이 잡힌다")
    void unlocksOnAbilityLevelUpAlone() {
        // 출석 미션 10점. 출석 레벨은 1→2 로 오르지만 총 학습은 40점이 필요해 1 그대로다.
        MissionProgress progress = completeMission(user.getId(), DAILY_ATTEND);

        MissionClaimResponseDto response = missionService.claim(user.getId(), progress.getId());

        assertThat(response.leveledUp())
                .as("총 학습 레벨은 안 올랐다")
                .isFalse();
        assertThat(response.totalStudyLevel()).isEqualTo(1L);
        assertThat(response.unlockedCosmetics())
                .extracting(UnlockedCosmeticDto::itemKey)
                .as("출석 2 에 열리는 봄 배경이 여기서 빠지면 능력치 해금이 사용자에게 보이지 않는다")
                .containsExactly("bg_spring");
    }

    @Test
    @DisplayName("능력치와 총 학습이 같이 오르면 둘 다 실린다")
    void unlocksFromBothRanges() {
        // 주간 출석 100점. 출석 1→5, 총 학습 1→2 가 된다.
        MissionProgress progress = completeMission(user.getId(), WEEKLY_ATTEND_5);

        MissionClaimResponseDto response = missionService.claim(user.getId(), progress.getId());

        assertThat(response.leveledUp()).isTrue();
        assertThat(response.totalStudyLevel()).isEqualTo(2L);
        assertThat(response.unlockedCosmetics())
                .extracting(UnlockedCosmeticDto::itemKey)
                .as("출석 2~5 여섯 개와 총 학습 2 하나. 레벨 순, 같은 레벨이면 키 순이다")
                .containsExactly("bg_spring", "headband_sprout", "effect_petals", "frame_spring",
                        "bg_summer", "effect_sparkle", "frame_summer");
        assertThat(response.unlockedCosmetics().get(0).slot()).isEqualTo(CosmeticSlot.BACKGROUND);
        assertThat(response.unlockedCosmetics().get(0).nameKo()).isEqualTo("봄 배경");
    }

    @Test
    @DisplayName("XP 가 들어간 능력치의 것만 실린다")
    void onlyTheRewardedAbilityUnlocks() {
        // 주간 오답노트 80점. 작성 1→4, 총 학습 1→2 가 된다.
        MissionProgress progress = completeMission(user.getId(), WEEKLY_NOTE_10);

        MissionClaimResponseDto response = missionService.claim(user.getId(), progress.getId());

        assertThat(response.unlockedCosmetics())
                .extracting(UnlockedCosmeticDto::itemKey)
                .as("작성 2·3 과 총 학습 2 뿐이다")
                .containsExactly("bag_mini_backpack", "headband_sprout", "prop_notebook");
        assertThat(response.unlockedCosmetics())
                .extracting(UnlockedCosmeticDto::itemKey)
                .as("출석 2 짜리 봄 배경과 출석 3 짜리 봄 프레임은 출석이 오르지 않았으니 열리지 않는다")
                .doesNotContain("bg_spring", "frame_spring", "glasses_round", "scarf");
    }

    @Test
    @DisplayName("한 번에 여러 단계 오르면 그 사이 것이 전부 실린다")
    void unlocksEveryLevelInRange() {
        // 주간 4종(출석100 + 작성80 + 복습100 + 세트80 = 360)을 몰아 받는다.
        for (String code : new String[]{WEEKLY_ATTEND_5, WEEKLY_NOTE_10, WEEKLY_REVIEW_30, WEEKLY_SET_3}) {
            completeMission(user.getId(), code);
        }

        MissionClaimResponseDto last = null;
        for (String code : new String[]{WEEKLY_ATTEND_5, WEEKLY_NOTE_10, WEEKLY_REVIEW_30, WEEKLY_SET_3}) {
            last = missionService.claim(user.getId(), progressOf(user, code).getId());
        }

        assertThat(last).isNotNull();
        assertThat(last.totalStudyLevel()).isGreaterThan(2L);
        // 마지막 수령까지의 해금을 다 합치면 현재 총 학습 레벨까지 열린 것이 빠짐없이 나왔어야 한다.
        assertThat(cosmeticService.findUnlockedBetween(1, last.totalStudyLevel()))
                .extracting(UnlockedCosmeticDto::itemKey)
                .contains("headband_sprout", "badge_leaf_star");
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
