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

    @Nested
    @DisplayName("아이템 목록")
    class ItemCatalog {

        @Test
        @DisplayName("잠긴 아이템도 owned false 로 함께 내려간다")
        void includesLockedItems() {
            User user = userAtLevel(6);

            CosmeticListResponseDto response = cosmeticService.getCosmetics(user.getId());

            assertThat(ownedOf(response, HAT_BEANIE))
                    .as("레벨 6 아이템은 레벨 6 에서 열린다 - 경계는 포함이다")
                    .isTrue();
            assertThat(ownedOf(response, BG_STUDY))
                    .as("레벨 7 아이템은 아직 잠겨 있지만 목록에는 있어야 한다")
                    .isFalse();
            assertThat(itemKeys(response)).contains(BG_STUDY);
        }

        @ParameterizedTest(name = "레벨 {0} 이면 레벨 {1} 아이템 보유는 {2}")
        @CsvSource({
                "5, 6, false",
                "6, 6, true",
                "7, 6, true",
        })
        @DisplayName("해금 경계는 required_level == 레벨 을 포함한다")
        void unlockBoundaryIsInclusive(long userLevel, int itemLevel, boolean expected) {
            User user = userAtLevel(userLevel);
            // 비교 대상은 시드된 레벨 6 아이템(비니) 하나다.
            assertThat(itemLevel).isEqualTo(6);

            assertThat(ownedOf(cosmeticService.getCosmetics(user.getId()), HAT_BEANIE)).isEqualTo(expected);
        }

        @Test
        @DisplayName("비활성 아이템은 목록에 나오지 않는다")
        void excludesInactiveItems() {
            User user = userAtLevel(15);

            assertThat(itemKeys(cosmeticService.getCosmetics(user.getId())))
                    .as("2차 콘텐츠가 목록에 새어 나가면 안 된다")
                    .doesNotContain(INACTIVE_HAT_BERET);
        }

        @Test
        @DisplayName("개구리 본체는 아이템이 아니라 baseImageUrl 로 나간다")
        void baseIsNotAnItem() {
            User user = userAtLevel(15);

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
            User user = userAtLevel(1);

            List<CosmeticSlotDto> slots = cosmeticService.getCosmetics(user.getId()).slots();

            assertThat(slots).extracting(CosmeticSlotDto::slot)
                    .containsExactly(
                            CosmeticSlot.BACKGROUND, CosmeticSlot.BACK, CosmeticSlot.OUTFIT,
                            CosmeticSlot.NECK, CosmeticSlot.FACE, CosmeticSlot.HEAD,
                            CosmeticSlot.HAND, CosmeticSlot.BADGE, CosmeticSlot.EFFECT);
            assertThat(slots).extracting(CosmeticSlotDto::layerOrder)
                    .containsExactly(100, 200, 400, 500, 600, 700, 800, 850, 900);
            assertThat(slots.get(0).nameKo()).isEqualTo("배경");
        }

        @Test
        @DisplayName("이미지 경로는 번들 상대 경로다 - S3 전환은 컬럼 값만 바꾸면 된다")
        void imageUrlIsBundlePath() {
            User user = userAtLevel(6);

            assertThat(itemOf(cosmeticService.getCosmetics(user.getId()), HAT_BEANIE).imageUrl())
                    .isEqualTo("assets/Cosmetic/hat_beanie.png");
        }

        @Test
        @DisplayName("충돌 목록은 비어 있어도 빈 배열로 나간다")
        void conflictsAreEmptyList() {
            User user = userAtLevel(6);

            assertThat(itemOf(cosmeticService.getCosmetics(user.getId()), HAT_BEANIE).conflictsWith()).isEmpty();
        }
    }

    @Nested
    @DisplayName("기본 프리셋")
    class DefaultPreset {

        @Test
        @DisplayName("장착 행이 없는 사용자는 슬롯마다 가장 높은 해금 아이템을 걸고 있다")
        void presetPicksHighestUnlockedPerSlot() {
            User user = userAtLevel(15);

            assertThat(equippedOf(user.getId()))
                    .containsEntry(CosmeticSlot.HEAD, HAT_GRADUATE)
                    .containsEntry(CosmeticSlot.BACKGROUND, BG_NIGHT)
                    .containsEntry(CosmeticSlot.OUTFIT, OUTFIT_GRADUATE)
                    .containsEntry(CosmeticSlot.HAND, PROP_DIPLOMA)
                    .containsEntry(CosmeticSlot.FACE, GLASSES_SUN)
                    .containsEntry(CosmeticSlot.NECK, SCARF)
                    .containsEntry(CosmeticSlot.BACK, BAG_MINI_BACKPACK)
                    .doesNotContainKey(CosmeticSlot.BADGE)
                    .doesNotContainKey(CosmeticSlot.EFFECT);
        }

        @Test
        @DisplayName("레벨이 낮으면 열린 것만 걸린다")
        void presetFollowsLevel() {
            User user = userAtLevel(6);

            assertThat(equippedOf(user.getId()))
                    .containsEntry(CosmeticSlot.HEAD, HAT_BEANIE)
                    .containsEntry(CosmeticSlot.BACKGROUND, BG_SPRING)
                    .containsEntry(CosmeticSlot.FACE, GLASSES_ROUND)
                    .containsEntry(CosmeticSlot.NECK, SCARF)
                    .as("레벨 8 가방은 아직 안 열렸다")
                    .doesNotContainKey(CosmeticSlot.BACK);
        }

        @Test
        @DisplayName("레벨 1 은 아무것도 걸치지 않은 맨 개구리다")
        void level1HasNothing() {
            User user = userAtLevel(1);

            assertThat(equippedOf(user.getId())).isEmpty();
        }

        @Test
        @DisplayName("해금 레벨이 같으면 키 순서로 고정된다 - 조회할 때마다 달라지면 안 된다")
        void presetTieIsStable() {
            User user = userAtLevel(15);
            // hat_bucket 을 학사모와 같은 레벨로 올려 동점을 만든다.
            jdbcTemplate.update("UPDATE cosmetic_item SET required_level = 15 WHERE item_key = ?", HAT_BUCKET);

            assertThat(equippedOf(user.getId()))
                    .containsEntry(CosmeticSlot.HEAD, HAT_BUCKET);
            assertThat(equippedOf(user.getId()))
                    .as("같은 요청을 두 번 해도 같아야 한다")
                    .containsEntry(CosmeticSlot.HEAD, HAT_BUCKET);
        }

        @Test
        @DisplayName("조회만으로는 행이 생기지 않는다")
        void readingDoesNotCreateRows() {
            User user = userAtLevel(15);

            cosmeticService.getCosmetics(user.getId());
            cosmeticService.getCosmetics(user.getId());

            assertThat(loadoutRowCount(user.getId()))
                    .as("목록만 열어 본 사용자 수만큼 빈 행이 생기면 안 된다")
                    .isZero();
        }

        @Test
        @DisplayName("첫 장착 때 프리셋이 행으로 굳어 나머지 슬롯이 벗겨지지 않는다")
        void firstEquipMaterializesPreset() {
            User user = userAtLevel(15);

            cosmeticService.equip(user.getId(), CosmeticSlot.HEAD, HAT_BEANIE);

            assertThat(equippedOf(user.getId()))
                    .as("모자만 바꿨는데 배경·옷·가방이 사라지면 사용자 눈에는 하향이다")
                    .containsEntry(CosmeticSlot.HEAD, HAT_BEANIE)
                    .containsEntry(CosmeticSlot.BACKGROUND, BG_NIGHT)
                    .containsEntry(CosmeticSlot.OUTFIT, OUTFIT_GRADUATE)
                    .containsEntry(CosmeticSlot.HAND, PROP_DIPLOMA);
            assertThat(loadoutRowCount(user.getId())).isEqualTo(7);
        }

        @Test
        @DisplayName("한 번 장착한 뒤로는 레벨이 올라도 프리셋을 타지 않는다")
        void presetDoesNotComeBackAfterFirstChange() {
            User user = userAtLevel(6);
            cosmeticService.equip(user.getId(), CosmeticSlot.HEAD, HEADBAND_SPROUT);

            setLevel(user, 15);

            assertThat(equippedOf(user.getId()))
                    .as("사용자가 고른 머리띠를 레벨업이 학사모로 바꿔치기하면 안 된다")
                    .containsEntry(CosmeticSlot.HEAD, HEADBAND_SPROUT);
        }

        @Test
        @DisplayName("모든 슬롯을 벗어도 프리셋이 되살아나지 않는다")
        void unequippingEverythingSticks() {
            User user = userAtLevel(15);
            Map<CosmeticSlot, String> preset = equippedOf(user.getId());

            preset.keySet().forEach(slot -> cosmeticService.equip(user.getId(), slot, null));

            assertThat(equippedOf(user.getId()))
                    .as("맨 개구리를 보려고 다 벗은 사용자가 앱을 껐다 켜면 다시 입고 있으면 안 된다")
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("장착")
    class Equip {

        @Test
        @DisplayName("보유한 아이템을 걸면 갱신된 장착 상태 전체가 돌아온다")
        void equipsOwnedItem() {
            User user = userAtLevel(15);

            CosmeticEquipResponseDto response = cosmeticService.equip(user.getId(), CosmeticSlot.HEAD, HAT_BUCKET);

            assertThat(response.equipped()).containsEntry(CosmeticSlot.HEAD, HAT_BUCKET);
            assertThat(response.unequippedSlots()).isEmpty();
        }

        @Test
        @DisplayName("같은 슬롯을 다시 걸어도 행은 하나다")
        void equipIsIdempotentPerSlot() {
            User user = userAtLevel(15);

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
            User user = userAtLevel(15);

            CosmeticEquipResponseDto response = cosmeticService.equip(user.getId(), CosmeticSlot.HEAD, null);

            assertThat(response.equipped())
                    .doesNotContainKey(CosmeticSlot.HEAD)
                    .containsEntry(CosmeticSlot.BACKGROUND, BG_NIGHT);
        }

        @Test
        @DisplayName("해제는 행 삭제가 아니라 표시로 남는다")
        void unequipLeavesMarker() {
            User user = userAtLevel(15);

            cosmeticService.equip(user.getId(), CosmeticSlot.HEAD, null);

            assertThat(rawItemKeyOf(user.getId(), CosmeticSlot.HEAD))
                    .as("행을 지우면 한 번도 안 건드린 사용자와 구별되지 않아 프리셋이 되살아난다")
                    .isEqualTo(UserCosmeticLoadout.NONE);
        }

        @Test
        @DisplayName("보유하지 않은 아이템은 걸 수 없다")
        void rejectsUnownedItem() {
            User user = userAtLevel(5);

            assertThatThrownBy(() -> cosmeticService.equip(user.getId(), CosmeticSlot.HEAD, HAT_BEANIE))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(exception -> ((ApplicationException) exception).getErrorCase())
                    .isEqualTo(CosmeticErrorCase.COSMETIC_ITEM_NOT_OWNED);
            assertThat(loadoutRowCount(user.getId()))
                    .as("거절된 요청이 프리셋을 굳혀 놓고 가면 안 된다")
                    .isZero();
        }

        @Test
        @DisplayName("없는 아이템과 비활성 아이템은 같은 에러로 답한다")
        void hidesInactiveItems() {
            User user = userAtLevel(15);

            assertThatThrownBy(() -> cosmeticService.equip(user.getId(), CosmeticSlot.HEAD, "not_exists"))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(exception -> ((ApplicationException) exception).getErrorCase())
                    .isEqualTo(CosmeticErrorCase.COSMETIC_ITEM_NOT_FOUND);
            assertThatThrownBy(() -> cosmeticService.equip(user.getId(), CosmeticSlot.HEAD, INACTIVE_HAT_BERET))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(exception -> ((ApplicationException) exception).getErrorCase())
                    .as("비활성이라고 알려 주면 키를 훑어 2차 콘텐츠 목록을 알아낼 수 있다")
                    .isEqualTo(CosmeticErrorCase.COSMETIC_ITEM_NOT_FOUND);
        }

        @Test
        @DisplayName("슬롯과 아이템이 어긋나면 조용히 고쳐 주지 않고 거절한다")
        void rejectsSlotMismatch() {
            User user = userAtLevel(15);

            assertThatThrownBy(() -> cosmeticService.equip(user.getId(), CosmeticSlot.OUTFIT, HAT_BEANIE))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(exception -> ((ApplicationException) exception).getErrorCase())
                    .isEqualTo(CosmeticErrorCase.COSMETIC_SLOT_MISMATCH);
        }

        @Test
        @DisplayName("개구리 본체 슬롯에는 아무것도 걸 수 없다")
        void rejectsBaseSlot() {
            User user = userAtLevel(15);

            assertThatThrownBy(() -> cosmeticService.equip(user.getId(), CosmeticSlot.BASE, "BASE"))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(exception -> ((ApplicationException) exception).getErrorCase())
                    .isEqualTo(CosmeticErrorCase.COSMETIC_SLOT_MISMATCH);
        }

        @Test
        @DisplayName("남의 장착 상태는 건드리지 않는다")
        void doesNotTouchOtherUsers() {
            User user = userAtLevel(15);
            User other = userAtLevel(15);
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
            User user = userAtLevel(15);
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
            User user = userAtLevel(15);
            // 옷 쪽에만 적는다. 모자를 나중에 거는 순서에서도 걸려야 한다.
            setConflicts(OUTFIT_CARDIGAN, HAT_BEANIE);
            cosmeticService.equip(user.getId(), CosmeticSlot.OUTFIT, OUTFIT_CARDIGAN);

            CosmeticEquipResponseDto response = cosmeticService.equip(user.getId(), CosmeticSlot.HEAD, HAT_BEANIE);

            assertThat(response.unequippedSlots()).containsExactly(CosmeticSlot.OUTFIT);
        }

        @Test
        @DisplayName("여러 개가 걸리면 전부 벗긴다")
        void unequipsEveryConflict() {
            User user = userAtLevel(15);
            setConflicts(HAT_BEANIE, OUTFIT_GRADUATE + "," + SCARF);

            CosmeticEquipResponseDto response = cosmeticService.equip(user.getId(), CosmeticSlot.HEAD, HAT_BEANIE);

            assertThat(response.unequippedSlots())
                    .containsExactly(CosmeticSlot.NECK, CosmeticSlot.OUTFIT);
        }

        @Test
        @DisplayName("충돌 목록에 빈 조각이 섞여 있어도 엉뚱한 슬롯을 벗기지 않는다")
        void ignoresBlankConflictEntries() {
            User user = userAtLevel(15);
            setConflicts(HAT_BEANIE, ",, ,");

            CosmeticEquipResponseDto response = cosmeticService.equip(user.getId(), CosmeticSlot.HEAD, HAT_BEANIE);

            assertThat(response.unequippedSlots()).isEmpty();
        }

        @Test
        @DisplayName("충돌이 없으면 아무것도 벗기지 않는다")
        void noConflictKeepsEverything() {
            User user = userAtLevel(15);

            CosmeticEquipResponseDto response = cosmeticService.equip(user.getId(), CosmeticSlot.HEAD, HAT_BEANIE);

            assertThat(response.unequippedSlots()).isEmpty();
            assertThat(response.equipped()).hasSize(7);
        }
    }

    @Nested
    @DisplayName("세트 장착")
    class EquipSet {

        @Test
        @DisplayName("세트에 속한 아이템이 각자의 슬롯에 한 번에 걸린다")
        void equipsWholeSet() {
            User user = userAtLevel(15);
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
            User user = userAtLevel(14);

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
            User user = userAtLevel(15);

            assertThatThrownBy(() -> cosmeticService.equipSet(user.getId(), "not_exists"))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(exception -> ((ApplicationException) exception).getErrorCase())
                    .isEqualTo(CosmeticErrorCase.COSMETIC_SET_NOT_FOUND);
        }

        @Test
        @DisplayName("세트가 채우지 않는 슬롯은 그대로 남는다")
        void keepsUntouchedSlots() {
            User user = userAtLevel(15);

            CosmeticEquipResponseDto response = cosmeticService.equipSet(user.getId(), GRADUATE_SET);

            assertThat(response.equipped()).containsEntry(CosmeticSlot.BACKGROUND, BG_NIGHT);
        }

        @Test
        @DisplayName("세트와 충돌하는 다른 슬롯은 벗긴다")
        void unequipsConflictingSlot() {
            User user = userAtLevel(15);
            setConflicts(HAT_GRADUATE, SCARF);

            CosmeticEquipResponseDto response = cosmeticService.equipSet(user.getId(), GRADUATE_SET);

            assertThat(response.unequippedSlots()).containsExactly(CosmeticSlot.NECK);
            assertThat(response.equipped()).doesNotContainKey(CosmeticSlot.NECK);
        }
    }

    @Nested
    @DisplayName("레벨업 해금 목록")
    class UnlockLookup {

        @Test
        @DisplayName("올라간 구간에서 열린 것만 담긴다")
        void unlocksWithinRange() {
            List<UnlockedCosmeticDto> unlocked = cosmeticService.findUnlockedBetween(5, 7);

            assertThat(unlocked)
                    .extracting(UnlockedCosmeticDto::itemKey)
                    .as("구간은 (levelBefore, levelAfter] 다")
                    .containsExactly(HAT_BEANIE, BG_STUDY);
        }

        @Test
        @DisplayName("여러 단계를 한 번에 올라도 전부 담긴다")
        void unlocksAcrossManyLevels() {
            assertThat(cosmeticService.findUnlockedBetween(1, 15))
                    .extracting(UnlockedCosmeticDto::itemKey)
                    .hasSize(16)
                    .startsWith(HEADBAND_SPROUT)
                    .contains(HAT_GRADUATE, OUTFIT_GRADUATE, PROP_DIPLOMA);
        }

        @Test
        @DisplayName("레벨이 안 올랐으면 비어 있다")
        void emptyWhenLevelUnchanged() {
            assertThat(cosmeticService.findUnlockedBetween(6, 6)).isEmpty();
            assertThat(cosmeticService.findUnlockedBetween(7, 6)).isEmpty();
        }

        @Test
        @DisplayName("비활성 아이템은 해금 알림에 끼지 않는다")
        void skipsInactiveItems() {
            jdbcTemplate.update("UPDATE cosmetic_item SET active = 0 WHERE item_key = ?", HAT_BEANIE);

            assertThat(cosmeticService.findUnlockedBetween(5, 7))
                    .extracting(UnlockedCosmeticDto::itemKey)
                    .containsExactly(BG_STUDY);
        }

        @Test
        @DisplayName("해금 항목에는 이름과 이미지가 함께 실린다")
        void carriesDisplayFields() {
            UnlockedCosmeticDto unlocked = cosmeticService.findUnlockedBetween(5, 6).get(0);

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
