package com.aisip.OnO.backend.cosmetic.service;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.cosmetic.dto.CosmeticEquipResponseDto;
import com.aisip.OnO.backend.cosmetic.dto.CosmeticItemResponseDto;
import com.aisip.OnO.backend.cosmetic.dto.CosmeticListResponseDto;
import com.aisip.OnO.backend.cosmetic.dto.CosmeticSlotDto;
import com.aisip.OnO.backend.cosmetic.dto.UnlockedCosmeticDto;
import com.aisip.OnO.backend.cosmetic.entity.CosmeticSlot;
import com.aisip.OnO.backend.cosmetic.entity.UserCosmeticLoadout;
import com.aisip.OnO.backend.cosmetic.exception.CosmeticErrorCase;
import com.aisip.OnO.backend.cosmetic.support.CosmeticTestSupport;
import com.aisip.OnO.backend.mission.entity.MissionType.AbilityType;
import com.aisip.OnO.backend.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("꾸미기 서비스")
class CosmeticServiceTest extends CosmeticTestSupport {

    private static final String BADGE_HEART = "badge_heart";
    private static final String EFFECT_SPARKLE = "effect_sparkle";
    private static final String BG_RAINY = "bg_rainy";
    private static final String PROP_NOTEBOOK = "prop_notebook";

    @Nested
    @DisplayName("아이템 목록")
    class ItemCatalog {

        @Test
        @DisplayName("잠긴 아이템도 owned false 로 함께 내려간다")
        void includesLockedItems() {
            // 문제 복습만 4 인 사용자. 오답노트 작성은 1 이라 공부방 배경(작성 12)은 아직 잠겨 있다.
            User user = setLevels(fixtures.createUser(), 1L, 1L, 1L, 4L, 1L);

            CosmeticListResponseDto response = cosmeticService.getCosmetics(user.getId());

            assertThat(ownedOf(response, HAT_BEANIE))
                    .as("문제 복습 4 짜리 비니는 문제 복습이 4 가 되는 순간 열린다 - 경계는 포함이다")
                    .isTrue();
            assertThat(ownedOf(response, BG_STUDY))
                    .as("오답노트 작성 12 짜리 배경은 아직 잠겨 있지만 목록에는 있어야 한다")
                    .isFalse();
            assertThat(itemKeys(response)).contains(BG_STUDY);
        }

        @ParameterizedTest(name = "문제 복습 레벨 {0} 이면 비니(문제 복습 4) 보유는 {1}")
        @CsvSource({
                "3, false",
                "4, true",
                "5, true",
        })
        @DisplayName("해금 경계는 required_level == 그 능력치 레벨 을 포함한다")
        void unlockBoundaryIsInclusive(long problemPracticeLevel, boolean expected) {
            User user = setLevels(fixtures.createUser(), 1L, 1L, 1L, problemPracticeLevel, 1L);

            assertThat(ownedOf(cosmeticService.getCosmetics(user.getId()), HAT_BEANIE)).isEqualTo(expected);
        }

        @Test
        @DisplayName("능력치 하나만 높아도 다른 능력치 아이템은 열리지 않는다")
        void abilityLevelDoesNotLeakToOtherAbilities() {
            // 문제 복습만 15. 출석·작성·복습세트는 1 이다.
            User user = setLevels(fixtures.createUser(), 1L, 1L, 1L, 15L, 1L);

            CosmeticListResponseDto response = cosmeticService.getCosmetics(user.getId());

            assertThat(ownedOf(response, HEAD_EARMUFFS_WINTER))
                    .as("문제 복습의 마지막 아이템까지 열린다")
                    .isTrue();
            assertThat(ownedOf(response, BG_SPRING))
                    .as("출석 2 짜리 봄 배경이 문제 복습 15 로 열리면 능력치별 해금이 아니다")
                    .isFalse();
            assertThat(ownedOf(response, SCARF)).isFalse();
            assertThat(ownedOf(response, BAG_MINI_BACKPACK)).isFalse();
        }

        @Test
        @DisplayName("required_ability 가 비어 있는 아이템은 총 학습 레벨을 본다")
        void nullAbilityFallsBackToTotalLevel() {
            // 총 학습만 2, 능력치 넷은 1.
            User onlyTotal = userAtLevel(2);
            // 능력치 넷은 15, 총 학습은 1.
            User onlyAbilities = setLevels(fixtures.createUser(), 1L, 15L, 15L, 15L, 15L);

            assertThat(ownedOf(cosmeticService.getCosmetics(onlyTotal.getId()), HEADBAND_SPROUT))
                    .as("새싹 머리띠는 required_ability 가 비어 있어 총 학습 레벨 2 에서 열린다")
                    .isTrue();
            assertThat(ownedOf(cosmeticService.getCosmetics(onlyAbilities.getId()), HEADBAND_SPROUT))
                    .as("능력치를 아무리 올려도 총 학습 레벨이 1 이면 열리지 않는다")
                    .isFalse();
        }

        @Test
        @DisplayName("총 학습 레벨 16 이상에서 열리는 자리도 실제로 열린다")
        void unlocksAboveOldCap() {
            User user = userAtLevel(16);

            assertThat(ownedOf(cosmeticService.getCosmetics(user.getId()), PROP_LANTERN))
                    .as("상한이 15 에 머물면 이 아이템은 영영 열리지 않는다")
                    .isTrue();
            assertThat(ownedOf(cosmeticService.getCosmetics(user.getId()), BADGE_SNOWFLAKE)).isFalse();
        }

        @Test
        @DisplayName("비활성 아이템은 목록에 나오지 않는다")
        void excludesInactiveItems() {
            User user = fullyGrownUser();
            deactivate(HAT_BEANIE);

            assertThat(itemKeys(cosmeticService.getCosmetics(user.getId())))
                    .as("내려간 아이템이 목록에 새어 나가면 안 된다")
                    .doesNotContain(HAT_BEANIE);
        }

        @Test
        @DisplayName("시드된 63개가 빠짐없이 목록에 들어간다")
        void everySeededItemIsListed() {
            User user = fullyGrownUser();

            assertThat(itemKeys(cosmeticService.getCosmetics(user.getId())))
                    .as("본체(BASE)만 빠지고 나머지는 전부 나간다")
                    .hasSize(SEEDED_ITEM_COUNT);
        }

        @Test
        @DisplayName("개구리 본체는 아이템이 아니라 baseImageUrl 로 나간다")
        void baseIsNotAnItem() {
            User user = fullyGrownUser();

            CosmeticListResponseDto response = cosmeticService.getCosmetics(user.getId());

            assertThat(response.baseImageUrl()).isEqualTo("assets/Cosmetic/BASE.png");
            assertThat(response.baseLayerOrder()).isEqualTo(300);
            assertThat(itemKeys(response)).doesNotContain("BASE");
            assertThat(response.items())
                    .extracting(CosmeticItemResponseDto::slot)
                    .doesNotContain(CosmeticSlot.BASE);
        }

        @Test
        @DisplayName("슬롯 목록은 장착 가능한 것만, 뒤에서 앞 순서로 나간다")
        void slotsAreOrderedBackToFront() {
            User user = userAtAllLevels(1);

            List<CosmeticSlotDto> slots = cosmeticService.getCosmetics(user.getId()).slots();

            assertThat(slots).extracting(CosmeticSlotDto::slot)
                    .containsExactly(
                            CosmeticSlot.BACKGROUND, CosmeticSlot.BACK, CosmeticSlot.OUTFIT,
                            CosmeticSlot.BAG, CosmeticSlot.NECK, CosmeticSlot.FACE,
                            CosmeticSlot.HEAD, CosmeticSlot.HAND, CosmeticSlot.BADGE,
                            CosmeticSlot.EFFECT, CosmeticSlot.FRAME);
            assertThat(slots).extracting(CosmeticSlotDto::layerOrder)
                    .as("등짐(200)은 본체(300) 뒤, 앞가방(450)은 옷(400) 위다")
                    .containsExactly(100, 200, 400, 450, 500, 600, 700, 800, 850, 900, 1000);
            assertThat(slots.get(0).nameKo()).isEqualTo("배경");
        }

        @Test
        @DisplayName("프레임만 개구리 합성에서 빠진다 - composited 로 갈라 보낸다")
        void onlyFrameIsNotComposited() {
            User user = userAtAllLevels(1);

            List<CosmeticSlotDto> slots = cosmeticService.getCosmetics(user.getId()).slots();

            assertThat(slots)
                    .filteredOn(slot -> !slot.composited())
                    .as("layerOrder 만 내려보내면 프론트는 순서대로 겹치는 수밖에 없어 프레임이 개구리 위에 덮인다")
                    .extracting(CosmeticSlotDto::slot)
                    .containsExactly(CosmeticSlot.FRAME);
            assertThat(slots)
                    .filteredOn(CosmeticSlotDto::composited)
                    .as("나머지 열 자리는 전부 개구리에 겹쳐 그린다")
                    .hasSize(10);
        }

        @Test
        @DisplayName("프레임은 옷장 목록에서 빠지지 않는다")
        void frameItemsAreListed() {
            User user = fullyGrownUser();

            CosmeticListResponseDto response = cosmeticService.getCosmetics(user.getId());

            assertThat(response.items())
                    .filteredOn(item -> item.slot() == CosmeticSlot.FRAME)
                    .as("개구리에 안 겹친다고 목록에서 빼면 옷장에서 고를 수가 없다")
                    .extracting(CosmeticItemResponseDto::itemKey)
                    .containsExactlyInAnyOrder(FRAME_SPRING, FRAME_SUMMER, "frame_autumn", "frame_winter",
                            FRAME_NIGHT, FRAME_STUDY, FRAME_LEAF, FRAME_MASTER);
        }

        @Test
        @DisplayName("프레임 에셋만 SVG 이고 경로도 다르다")
        void frameAssetsAreSvg() {
            User user = fullyGrownUser();

            List<CosmeticItemResponseDto> items = cosmeticService.getCosmetics(user.getId()).items();

            assertThat(items)
                    .filteredOn(item -> item.slot() == CosmeticSlot.FRAME)
                    .allSatisfy(item -> assertThat(item.imageUrl())
                            .as(item.itemKey() + " 는 원형 테두리라 확대해도 깨지면 안 된다")
                            .isEqualTo("assets/ProfileFrame/" + item.itemKey() + ".svg"));
            assertThat(items)
                    .filteredOn(item -> item.slot() != CosmeticSlot.FRAME)
                    .allSatisfy(item -> assertThat(item.imageUrl())
                            .as("나머지는 그대로 번들 PNG 다")
                            .isEqualTo("assets/Cosmetic/" + item.itemKey() + ".png"));
        }

        @Test
        @DisplayName("프레임 해금도 능력치별 기준으로 판정된다")
        void frameUnlockFollowsAbility() {
            // 출석만 3. 봄 프레임(출석 3)은 열리고 여름 프레임(출석 5)은 아직이다.
            User user = setLevels(fixtures.createUser(), 1L, 3L, 1L, 1L, 1L);

            CosmeticListResponseDto response = cosmeticService.getCosmetics(user.getId());

            assertThat(ownedOf(response, FRAME_SPRING))
                    .as("경계는 포함이다")
                    .isTrue();
            assertThat(ownedOf(response, FRAME_SUMMER)).isFalse();
            assertThat(ownedOf(response, FRAME_STUDY))
                    .as("공부방 프레임은 작성 14 라 출석을 올려서는 안 열린다")
                    .isFalse();
            assertThat(ownedOf(response, FRAME_LEAF))
                    .as("잎새 프레임은 총 학습 4 다. required_ability 가 비어 있다")
                    .isFalse();
        }

        @Test
        @DisplayName("총 학습 프레임은 능력치를 아무리 올려도 안 열린다")
        void totalLevelFrameIgnoresAbilities() {
            User onlyAbilities = setLevels(fixtures.createUser(), 1L, 15L, 15L, 15L, 15L);
            User onlyTotal = userAtLevel(4);

            assertThat(ownedOf(cosmeticService.getCosmetics(onlyAbilities.getId()), FRAME_LEAF)).isFalse();
            assertThat(ownedOf(cosmeticService.getCosmetics(onlyTotal.getId()), FRAME_LEAF)).isTrue();
        }

        @Test
        @DisplayName("이미지 경로는 번들 상대 경로다 - S3 전환은 컬럼 값만 바꾸면 된다")
        void imageUrlIsBundlePath() {
            User user = fullyGrownUser();

            assertThat(itemOf(cosmeticService.getCosmetics(user.getId()), HAT_BEANIE).imageUrl())
                    .isEqualTo("assets/Cosmetic/hat_beanie.png");
        }

        @Test
        @DisplayName("충돌 목록은 비어 있어도 빈 배열로 나간다")
        void conflictsAreEmptyList() {
            User user = fullyGrownUser();

            assertThat(itemOf(cosmeticService.getCosmetics(user.getId()), HAT_BEANIE).conflictsWith()).isEmpty();
        }

        @Test
        @DisplayName("해금 조건·전신 여부·세트 이름이 아이템에 함께 실린다")
        void carriesUnlockAndDisplayFields() {
            User user = fullyGrownUser();
            CosmeticListResponseDto response = cosmeticService.getCosmetics(user.getId());

            CosmeticItemResponseDto glasses = itemOf(response, GLASSES_ROUND);
            assertThat(glasses.requiredAbility()).isEqualTo(AbilityType.PROBLEM_PRACTICE);
            assertThat(glasses.requiredLevel()).isEqualTo(2);
            assertThat(glasses.fullBody()).isFalse();
            assertThat(glasses.setId()).isNull();
            assertThat(glasses.setNameKo()).isNull();

            CosmeticItemResponseDto sprout = itemOf(response, HEADBAND_SPROUT);
            assertThat(sprout.requiredAbility())
                    .as("총 학습 레벨로 열리는 아이템은 능력치가 비어 있다")
                    .isNull();

            CosmeticItemResponseDto gown = itemOf(response, OUTFIT_GRADUATE);
            assertThat(gown.fullBody())
                    .as("전신 의상이면 앱이 본체를 머리만 있는 그림으로 바꿔 깐다")
                    .isTrue();
            assertThat(gown.setId()).isEqualTo(GRADUATE_SET);
            assertThat(gown.setNameKo()).isEqualTo(GRADUATE_SET_NAME);
        }

        @Test
        @DisplayName("전신 의상은 옷 다섯 벌 전부다")
        void everyOutfitIsFullBody() {
            User user = fullyGrownUser();

            List<String> fullBody = cosmeticService.getCosmetics(user.getId()).items().stream()
                    .filter(CosmeticItemResponseDto::fullBody)
                    .map(CosmeticItemResponseDto::itemKey)
                    .toList();

            assertThat(fullBody)
                    .as("소매와 바짓단이 그려진 옷을 빠뜨리면 그 옷만 개구리 팔다리가 삐져나온다")
                    .containsExactlyInAnyOrder(OUTFIT_CARDIGAN, "outfit_hoodie", "outfit_raincoat",
                            OUTFIT_SCHOOL, OUTFIT_GRADUATE);
        }
    }

    @Nested
    @DisplayName("기본 프리셋")
    class DefaultPreset {

        @Test
        @DisplayName("장착 행이 없는 사용자는 슬롯마다 가장 높은 해금 아이템을 걸고 있다")
        void presetPicksHighestUnlockedPerSlot() {
            User user = fullyGrownUser();

            assertThat(equippedOf(user.getId()))
                    .containsEntry(CosmeticSlot.BACKGROUND, BG_SPACE)
                    .containsEntry(CosmeticSlot.BACK, BACK_BACKPACK_CANVAS)
                    .containsEntry(CosmeticSlot.OUTFIT, OUTFIT_GRADUATE)
                    .containsEntry(CosmeticSlot.BAG, BAG_CROSSBODY_SATCHEL)
                    .containsEntry(CosmeticSlot.NECK, NECK_MEDAL)
                    .containsEntry(CosmeticSlot.FACE, "face_moustache")
                    .containsEntry(CosmeticSlot.HEAD, HAT_GRADUATE)
                    .containsEntry(CosmeticSlot.HAND, PROP_DIPLOMA)
                    .containsEntry(CosmeticSlot.BADGE, BADGE_SNOWFLAKE)
                    .containsEntry(CosmeticSlot.EFFECT, EFFECT_SNOW)
                    .containsEntry(CosmeticSlot.FRAME, FRAME_MASTER);
        }

        @Test
        @DisplayName("프리셋도 능력치별 기준으로 뽑힌다")
        void presetFollowsAbilityLevel() {
            // 출석만 6. 출석 아이템은 배경과 전경 효과뿐이다.
            User user = setLevels(fixtures.createUser(), 1L, 6L, 1L, 1L, 1L);

            assertThat(equippedOf(user.getId()))
                    .containsEntry(CosmeticSlot.BACKGROUND, BG_RAINY)
                    .containsEntry(CosmeticSlot.EFFECT, EFFECT_SPARKLE)
                    .as("출석 5 짜리 여름 프레임까지 열려 있다")
                    .containsEntry(CosmeticSlot.FRAME, FRAME_SUMMER)
                    .as("출석만 올렸는데 다른 능력치 자리가 채워지면 안 된다")
                    .doesNotContainKey(CosmeticSlot.HEAD)
                    .doesNotContainKey(CosmeticSlot.FACE)
                    .doesNotContainKey(CosmeticSlot.NECK)
                    .doesNotContainKey(CosmeticSlot.BAG);
        }

        @Test
        @DisplayName("총 학습 레벨만 높으면 총 학습 아이템만 걸린다")
        void presetWithOnlyTotalLevel() {
            User user = userAtLevel(20);

            assertThat(equippedOf(user.getId()))
                    .containsEntry(CosmeticSlot.HEAD, HAT_GRADUATE)
                    .containsEntry(CosmeticSlot.OUTFIT, OUTFIT_GRADUATE)
                    .containsEntry(CosmeticSlot.HAND, PROP_DIPLOMA)
                    .containsEntry(CosmeticSlot.BADGE, BADGE_SNOWFLAKE)
                    .as("프레임은 총 학습 17 짜리 마스터가 가장 늦게 열린다")
                    .containsEntry(CosmeticSlot.FRAME, FRAME_MASTER)
                    .as("배경·얼굴·목·가방은 전부 능력치로만 열린다")
                    .doesNotContainKey(CosmeticSlot.BACKGROUND)
                    .doesNotContainKey(CosmeticSlot.FACE)
                    .doesNotContainKey(CosmeticSlot.NECK)
                    .doesNotContainKey(CosmeticSlot.BAG)
                    .doesNotContainKey(CosmeticSlot.BACK)
                    .doesNotContainKey(CosmeticSlot.EFFECT);
        }

        @Test
        @DisplayName("레벨 1 은 아무것도 걸치지 않은 맨 개구리다")
        void level1HasNothing() {
            User user = userAtAllLevels(1);

            assertThat(equippedOf(user.getId())).isEmpty();
        }

        @Test
        @DisplayName("해금 레벨이 같으면 키 순서로 고정된다 - 조회할 때마다 달라지면 안 된다")
        void presetTieIsStable() {
            User user = fullyGrownUser();
            // 버킷햇을 학사모와 같은 조건으로 올려 동점을 만든다.
            jdbcTemplate.update(
                    "UPDATE cosmetic_item SET required_level = 20, required_ability = NULL WHERE item_key = ?",
                    HAT_BUCKET);

            assertThat(equippedOf(user.getId()))
                    .containsEntry(CosmeticSlot.HEAD, HAT_BUCKET);
            assertThat(equippedOf(user.getId()))
                    .as("같은 요청을 두 번 해도 같아야 한다")
                    .containsEntry(CosmeticSlot.HEAD, HAT_BUCKET);
        }

        @Test
        @DisplayName("조회만으로는 행이 생기지 않는다")
        void readingDoesNotCreateRows() {
            User user = fullyGrownUser();

            cosmeticService.getCosmetics(user.getId());
            cosmeticService.getCosmetics(user.getId());

            assertThat(loadoutRowCount(user.getId()))
                    .as("목록만 열어 본 사용자 수만큼 빈 행이 생기면 안 된다")
                    .isZero();
        }

        @Test
        @DisplayName("첫 장착 때 프리셋이 행으로 굳어 나머지 슬롯이 벗겨지지 않는다")
        void firstEquipMaterializesPreset() {
            User user = fullyGrownUser();

            cosmeticService.equip(user.getId(), CosmeticSlot.HEAD, HAT_BEANIE);

            assertThat(equippedOf(user.getId()))
                    .as("모자만 바꿨는데 배경·옷·가방이 사라지면 사용자 눈에는 하향이다")
                    .containsEntry(CosmeticSlot.HEAD, HAT_BEANIE)
                    .containsEntry(CosmeticSlot.BACKGROUND, BG_SPACE)
                    .containsEntry(CosmeticSlot.OUTFIT, OUTFIT_GRADUATE)
                    .containsEntry(CosmeticSlot.HAND, PROP_DIPLOMA);
            assertThat(loadoutRowCount(user.getId())).isEqualTo(EQUIPPABLE_SLOT_COUNT);
        }

        @Test
        @DisplayName("한 번 장착한 뒤로는 레벨이 올라도 프리셋을 타지 않는다")
        void presetDoesNotComeBackAfterFirstChange() {
            User user = setLevels(fixtures.createUser(), 2L, 1L, 1L, 4L, 1L);
            cosmeticService.equip(user.getId(), CosmeticSlot.HEAD, HEADBAND_SPROUT);

            setLevels(user, 20L, 15L, 15L, 15L, 15L);

            assertThat(equippedOf(user.getId()))
                    .as("사용자가 고른 머리띠를 레벨업이 학사모로 바꿔치기하면 안 된다")
                    .containsEntry(CosmeticSlot.HEAD, HEADBAND_SPROUT);
        }

        @Test
        @DisplayName("모든 슬롯을 벗어도 프리셋이 되살아나지 않는다")
        void unequippingEverythingSticks() {
            User user = fullyGrownUser();
            Map<CosmeticSlot, String> preset = equippedOf(user.getId());

            preset.keySet().forEach(slot -> cosmeticService.equip(user.getId(), slot, null));

            assertThat(equippedOf(user.getId()))
                    .as("맨 개구리를 보려고 다 벗은 사용자가 앱을 껐다 켜면 다시 입고 있으면 안 된다")
                    .isEmpty();
        }

        @Test
        @DisplayName("아이템이 다른 슬롯으로 옮겨 가면 예전 행은 보여주지 않는다")
        void staleSlotRowIsHidden() {
            User user = fullyGrownUser();
            cosmeticService.equip(user.getId(), CosmeticSlot.BAG, BAG_MINI_BACKPACK);
            // 카탈로그에서 미니 백팩을 등짐으로 옮긴다. 사용자 행은 여전히 BAG 을 가리킨다.
            jdbcTemplate.update("UPDATE cosmetic_item SET slot = 'BACK' WHERE item_key = ?", BAG_MINI_BACKPACK);

            assertThat(equippedOf(user.getId()))
                    .as("앞가방 자리에 등짐을 그리면 개구리 앞에 백팩이 떠 있게 된다")
                    .doesNotContainEntry(CosmeticSlot.BAG, BAG_MINI_BACKPACK);
        }
    }

    @Nested
    @DisplayName("장착")
    class Equip {

        @Test
        @DisplayName("보유한 아이템을 걸면 갱신된 장착 상태 전체가 돌아온다")
        void equipsOwnedItem() {
            User user = fullyGrownUser();

            CosmeticEquipResponseDto response = cosmeticService.equip(user.getId(), CosmeticSlot.HEAD, HAT_BUCKET);

            assertThat(response.equipped()).containsEntry(CosmeticSlot.HEAD, HAT_BUCKET);
            assertThat(response.unequippedSlots()).isEmpty();
        }

        @Test
        @DisplayName("등짐과 앞가방은 서로 다른 자리라 같이 걸린다")
        void backAndBagCoexist() {
            User user = fullyGrownUser();

            cosmeticService.equip(user.getId(), CosmeticSlot.BACK, BACK_BACKPACK_NAVY);
            CosmeticEquipResponseDto response =
                    cosmeticService.equip(user.getId(), CosmeticSlot.BAG, BAG_MINI_BACKPACK);

            assertThat(response.equipped())
                    .containsEntry(CosmeticSlot.BACK, BACK_BACKPACK_NAVY)
                    .containsEntry(CosmeticSlot.BAG, BAG_MINI_BACKPACK);
        }

        @Test
        @DisplayName("같은 슬롯을 다시 걸어도 행은 하나다")
        void equipIsIdempotentPerSlot() {
            User user = fullyGrownUser();

            cosmeticService.equip(user.getId(), CosmeticSlot.HEAD, HAT_BUCKET);
            cosmeticService.equip(user.getId(), CosmeticSlot.HEAD, HAT_CROWN);
            cosmeticService.equip(user.getId(), CosmeticSlot.HEAD, HAT_BEANIE);

            Long headRows = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM user_cosmetic_loadout WHERE user_id = ? AND slot = 'HEAD'",
                    Long.class, user.getId());
            assertThat(headRows).as("(user_id, slot) 기본키가 한 슬롯 한 행을 보장한다").isEqualTo(1);
            assertThat(equippedOf(user.getId())).containsEntry(CosmeticSlot.HEAD, HAT_BEANIE);
        }

        @Test
        @DisplayName("itemKey 가 null 이면 그 슬롯만 벗는다")
        void nullItemKeyUnequipsSlot() {
            User user = fullyGrownUser();

            CosmeticEquipResponseDto response = cosmeticService.equip(user.getId(), CosmeticSlot.HEAD, null);

            assertThat(response.equipped())
                    .doesNotContainKey(CosmeticSlot.HEAD)
                    .containsEntry(CosmeticSlot.BACKGROUND, BG_SPACE);
        }

        @Test
        @DisplayName("해제는 행 삭제가 아니라 표시로 남는다")
        void unequipLeavesMarker() {
            User user = fullyGrownUser();

            cosmeticService.equip(user.getId(), CosmeticSlot.HEAD, null);

            assertThat(rawItemKeyOf(user.getId(), CosmeticSlot.HEAD))
                    .as("행을 지우면 한 번도 안 건드린 사용자와 구별되지 않아 프리셋이 되살아난다")
                    .isEqualTo(UserCosmeticLoadout.NONE);
        }

        @Test
        @DisplayName("그 능력치 레벨이 모자라면 걸 수 없다")
        void rejectsUnownedItem() {
            // 총 학습은 20 이지만 문제 복습이 3 이라 비니(문제 복습 4)는 아직 잠겨 있다.
            User user = setLevels(fixtures.createUser(), 20L, 15L, 15L, 3L, 15L);

            assertThatThrownBy(() -> cosmeticService.equip(user.getId(), CosmeticSlot.HEAD, HAT_BEANIE))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(exception -> ((ApplicationException) exception).getErrorCase())
                    .as("총 학습 레벨이 높다고 능력치 아이템이 열리면 안 된다")
                    .isEqualTo(CosmeticErrorCase.COSMETIC_ITEM_NOT_OWNED);
            assertThat(loadoutRowCount(user.getId()))
                    .as("거절된 요청이 프리셋을 굳혀 놓고 가면 안 된다")
                    .isZero();
        }

        @Test
        @DisplayName("없는 아이템과 비활성 아이템은 같은 에러로 답한다")
        void hidesInactiveItems() {
            User user = fullyGrownUser();
            deactivate(HAT_BUCKET);

            assertThatThrownBy(() -> cosmeticService.equip(user.getId(), CosmeticSlot.HEAD, "not_exists"))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(exception -> ((ApplicationException) exception).getErrorCase())
                    .isEqualTo(CosmeticErrorCase.COSMETIC_ITEM_NOT_FOUND);
            assertThatThrownBy(() -> cosmeticService.equip(user.getId(), CosmeticSlot.HEAD, HAT_BUCKET))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(exception -> ((ApplicationException) exception).getErrorCase())
                    .as("비활성이라고 알려 주면 키를 훑어 공개 전 콘텐츠 목록을 알아낼 수 있다")
                    .isEqualTo(CosmeticErrorCase.COSMETIC_ITEM_NOT_FOUND);
        }

        @Test
        @DisplayName("슬롯과 아이템이 어긋나면 조용히 고쳐 주지 않고 거절한다")
        void rejectsSlotMismatch() {
            User user = fullyGrownUser();

            assertThatThrownBy(() -> cosmeticService.equip(user.getId(), CosmeticSlot.OUTFIT, HAT_BEANIE))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(exception -> ((ApplicationException) exception).getErrorCase())
                    .isEqualTo(CosmeticErrorCase.COSMETIC_SLOT_MISMATCH);
            assertThatThrownBy(() -> cosmeticService.equip(user.getId(), CosmeticSlot.BACK, BAG_MINI_BACKPACK))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(exception -> ((ApplicationException) exception).getErrorCase())
                    .as("앞가방을 등짐 자리에 걸면 레이어가 뒤집힌다")
                    .isEqualTo(CosmeticErrorCase.COSMETIC_SLOT_MISMATCH);
        }

        @Test
        @DisplayName("개구리 본체 슬롯에는 아무것도 걸 수 없다")
        void rejectsBaseSlot() {
            User user = fullyGrownUser();

            assertThatThrownBy(() -> cosmeticService.equip(user.getId(), CosmeticSlot.BASE, "BASE"))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(exception -> ((ApplicationException) exception).getErrorCase())
                    .isEqualTo(CosmeticErrorCase.COSMETIC_SLOT_MISMATCH);
        }

        @Test
        @DisplayName("남의 장착 상태는 건드리지 않는다")
        void doesNotTouchOtherUsers() {
            User user = fullyGrownUser();
            User other = fullyGrownUser();
            cosmeticService.equip(other.getId(), CosmeticSlot.HEAD, HAT_BEANIE);

            cosmeticService.equip(user.getId(), CosmeticSlot.HEAD, HAT_BUCKET);

            assertThat(equippedOf(other.getId())).containsEntry(CosmeticSlot.HEAD, HAT_BEANIE);
        }
    }

    @Nested
    @DisplayName("충돌 자동 해제")
    class ConflictResolution {

        @Test
        @DisplayName("충돌하는 다른 슬롯은 벗겨지고 벗긴 슬롯이 응답에 담긴다")
        void unequipsConflictingSlot() {
            User user = fullyGrownUser();
            setConflicts(HAT_BEANIE, OUTFIT_CARDIGAN);
            cosmeticService.equip(user.getId(), CosmeticSlot.OUTFIT, OUTFIT_CARDIGAN);

            CosmeticEquipResponseDto response = cosmeticService.equip(user.getId(), CosmeticSlot.HEAD, HAT_BEANIE);

            assertThat(response.unequippedSlots()).containsExactly(CosmeticSlot.OUTFIT);
            assertThat(response.equipped())
                    .containsEntry(CosmeticSlot.HEAD, HAT_BEANIE)
                    .doesNotContainKey(CosmeticSlot.OUTFIT);
        }

        @Test
        @DisplayName("충돌은 반대쪽에만 적혀 있어도 잡힌다")
        void conflictIsBidirectional() {
            User user = fullyGrownUser();
            // 옷 쪽에만 적는다. 모자를 나중에 거는 순서에서도 걸려야 한다.
            setConflicts(OUTFIT_CARDIGAN, HAT_BEANIE);
            cosmeticService.equip(user.getId(), CosmeticSlot.OUTFIT, OUTFIT_CARDIGAN);

            CosmeticEquipResponseDto response = cosmeticService.equip(user.getId(), CosmeticSlot.HEAD, HAT_BEANIE);

            assertThat(response.unequippedSlots()).containsExactly(CosmeticSlot.OUTFIT);
        }

        @Test
        @DisplayName("여러 개가 걸리면 전부 벗긴다")
        void unequipsEveryConflict() {
            User user = fullyGrownUser();
            setConflicts(HAT_BEANIE, OUTFIT_GRADUATE + "," + NECK_MEDAL);

            CosmeticEquipResponseDto response = cosmeticService.equip(user.getId(), CosmeticSlot.HEAD, HAT_BEANIE);

            assertThat(response.unequippedSlots())
                    .containsExactly(CosmeticSlot.NECK, CosmeticSlot.OUTFIT);
        }

        @Test
        @DisplayName("충돌 목록에 빈 조각이 섞여 있어도 엉뚱한 슬롯을 벗기지 않는다")
        void ignoresBlankConflictEntries() {
            User user = fullyGrownUser();
            setConflicts(HAT_BEANIE, ",, ,");

            CosmeticEquipResponseDto response = cosmeticService.equip(user.getId(), CosmeticSlot.HEAD, HAT_BEANIE);

            assertThat(response.unequippedSlots()).isEmpty();
        }

        @Test
        @DisplayName("충돌이 없으면 아무것도 벗기지 않는다")
        void noConflictKeepsEverything() {
            User user = fullyGrownUser();

            CosmeticEquipResponseDto response = cosmeticService.equip(user.getId(), CosmeticSlot.HEAD, HAT_BEANIE);

            assertThat(response.unequippedSlots()).isEmpty();
            assertThat(response.equipped()).hasSize(EQUIPPABLE_SLOT_COUNT);
        }
    }

    @Nested
    @DisplayName("세트 장착")
    class EquipSet {

        @Test
        @DisplayName("세트에 속한 아이템이 각자의 슬롯에 한 번에 걸린다")
        void equipsWholeSet() {
            User user = fullyGrownUser();
            cosmeticService.equip(user.getId(), CosmeticSlot.HEAD, HAT_BEANIE);

            CosmeticEquipResponseDto response = cosmeticService.equipSet(user.getId(), GRADUATE_SET);

            assertThat(response.equipped())
                    .containsEntry(CosmeticSlot.HEAD, HAT_GRADUATE)
                    .containsEntry(CosmeticSlot.OUTFIT, OUTFIT_GRADUATE)
                    .containsEntry(CosmeticSlot.HAND, PROP_DIPLOMA);
        }

        @Test
        @DisplayName("하나라도 미보유면 통째로 거절한다")
        void rejectsPartiallyOwnedSet() {
            User user = setLevels(fixtures.createUser(), 19L, 15L, 15L, 15L, 15L);

            assertThatThrownBy(() -> cosmeticService.equipSet(user.getId(), GRADUATE_SET))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(exception -> ((ApplicationException) exception).getErrorCase())
                    .isEqualTo(CosmeticErrorCase.COSMETIC_ITEM_NOT_OWNED);
            assertThat(loadoutRowCount(user.getId()))
                    .as("거절된 요청은 아무것도 쓰지 않는다")
                    .isZero();
        }

        @Test
        @DisplayName("없는 세트는 거절한다")
        void rejectsUnknownSet() {
            User user = fullyGrownUser();

            assertThatThrownBy(() -> cosmeticService.equipSet(user.getId(), "not_exists"))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(exception -> ((ApplicationException) exception).getErrorCase())
                    .isEqualTo(CosmeticErrorCase.COSMETIC_SET_NOT_FOUND);
        }

        @Test
        @DisplayName("세트가 채우지 않는 슬롯은 그대로 남는다")
        void keepsUntouchedSlots() {
            User user = fullyGrownUser();

            CosmeticEquipResponseDto response = cosmeticService.equipSet(user.getId(), GRADUATE_SET);

            assertThat(response.equipped()).containsEntry(CosmeticSlot.BACKGROUND, BG_SPACE);
        }

        @Test
        @DisplayName("세트와 충돌하는 다른 슬롯은 벗긴다")
        void unequipsConflictingSlot() {
            User user = fullyGrownUser();
            setConflicts(HAT_GRADUATE, NECK_MEDAL);

            CosmeticEquipResponseDto response = cosmeticService.equipSet(user.getId(), GRADUATE_SET);

            assertThat(response.unequippedSlots()).containsExactly(CosmeticSlot.NECK);
            assertThat(response.equipped()).doesNotContainKey(CosmeticSlot.NECK);
        }
    }

    @Nested
    @DisplayName("레벨업 해금 목록")
    class UnlockLookup {

        @Test
        @DisplayName("총 학습 구간에서 열린 것만 담긴다")
        void unlocksWithinTotalRange() {
            List<UnlockedCosmeticDto> unlocked = cosmeticService.findUnlockedBetween(1, 3);

            assertThat(unlocked)
                    .extracting(UnlockedCosmeticDto::itemKey)
                    .as("구간은 (levelBefore, levelAfter] 다")
                    .containsExactly(HEADBAND_SPROUT, BADGE_LEAF_STAR);
        }

        @Test
        @DisplayName("총 학습 구간에 능력치 아이템이 끼어들지 않는다")
        void totalRangeExcludesAbilityItems() {
            assertThat(cosmeticService.findUnlockedBetween(1, 20))
                    .extracting(UnlockedCosmeticDto::itemKey)
                    .as("오르지도 않은 능력치의 아이템이 '방금 열렸다' 고 나가면 안 된다")
                    .doesNotContain(GLASSES_ROUND, BG_SPRING, SCARF, BAG_MINI_BACKPACK)
                    .hasSize(16);
        }

        @Test
        @DisplayName("능력치 레벨이 오른 구간에서 열린 것이 담긴다")
        void unlocksWithinAbilityRange() {
            List<UnlockedCosmeticDto> unlocked = cosmeticService.findUnlockedBetween(
                    1, 1, AbilityType.PROBLEM_PRACTICE, 1, 4);

            assertThat(unlocked)
                    .extracting(UnlockedCosmeticDto::itemKey)
                    .containsExactly(GLASSES_ROUND, HAT_BEANIE);
        }

        @Test
        @DisplayName("총 학습과 능력치가 같이 오르면 둘 다 담기고 레벨 순으로 정렬된다")
        void mergesTotalAndAbilityUnlocks() {
            List<UnlockedCosmeticDto> unlocked = cosmeticService.findUnlockedBetween(
                    1, 2, AbilityType.ATTENDANCE, 1, 3);

            assertThat(unlocked)
                    .extracting(UnlockedCosmeticDto::itemKey)
                    .as("레벨 2 는 봄 배경과 새싹 머리띠, 레벨 3 은 꽃잎 효과와 봄 프레임이다")
                    .containsExactly(BG_SPRING, HEADBAND_SPROUT, EFFECT_PETALS, FRAME_SPRING);
        }

        @Test
        @DisplayName("능력치를 넘겨도 그 능력치 것만 나온다")
        void abilityRangeIsScopedToThatAbility() {
            assertThat(cosmeticService.findUnlockedBetween(1, 1, AbilityType.NOTE_WRITE, 1, 3))
                    .extracting(UnlockedCosmeticDto::itemKey)
                    .as("작성 2·3 은 미니 백팩과 공책이다. 같은 레벨의 출석·복습 아이템은 끼면 안 된다")
                    .containsExactly(BAG_MINI_BACKPACK, PROP_NOTEBOOK);
        }

        @Test
        @DisplayName("레벨이 안 올랐으면 비어 있다")
        void emptyWhenLevelUnchanged() {
            assertThat(cosmeticService.findUnlockedBetween(6, 6)).isEmpty();
            assertThat(cosmeticService.findUnlockedBetween(7, 6)).isEmpty();
            assertThat(cosmeticService.findUnlockedBetween(6, 6, AbilityType.ATTENDANCE, 6, 6)).isEmpty();
        }

        @Test
        @DisplayName("능력치가 없는 보상이면 총 학습 구간만 본다")
        void nullAbilityLooksAtTotalOnly() {
            assertThat(cosmeticService.findUnlockedBetween(1, 2, null, 0, 0))
                    .extracting(UnlockedCosmeticDto::itemKey)
                    .containsExactly(HEADBAND_SPROUT);
        }

        @Test
        @DisplayName("비활성 아이템은 해금 알림에 끼지 않는다")
        void skipsInactiveItems() {
            deactivate(HAT_BEANIE);

            assertThat(cosmeticService.findUnlockedBetween(1, 1, AbilityType.PROBLEM_PRACTICE, 1, 4))
                    .extracting(UnlockedCosmeticDto::itemKey)
                    .containsExactly(GLASSES_ROUND);
        }

        @Test
        @DisplayName("해금 항목에는 이름과 이미지가 함께 실린다")
        void carriesDisplayFields() {
            UnlockedCosmeticDto unlocked = cosmeticService
                    .findUnlockedBetween(1, 1, AbilityType.PROBLEM_PRACTICE, 3, 4).get(0);

            assertThat(unlocked.itemKey()).isEqualTo(HAT_BEANIE);
            assertThat(unlocked.nameKo()).isEqualTo("비니");
            assertThat(unlocked.slot()).isEqualTo(CosmeticSlot.HEAD);
            assertThat(unlocked.imageUrl()).isEqualTo("assets/Cosmetic/hat_beanie.png");
        }
    }

    // ─────────────────────────── 헬퍼 ───────────────────────────

    private List<String> itemKeys(CosmeticListResponseDto response) {
        return response.items().stream().map(CosmeticItemResponseDto::itemKey).toList();
    }

    private CosmeticItemResponseDto itemOf(CosmeticListResponseDto response, String itemKey) {
        return response.items().stream()
                .filter(item -> item.itemKey().equals(itemKey))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("목록에 없는 아이템이다: " + itemKey));
    }

    private boolean ownedOf(CosmeticListResponseDto response, String itemKey) {
        return itemOf(response, itemKey).owned();
    }
}
