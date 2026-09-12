package com.aisip.OnO.backend.concurrency;

import com.aisip.OnO.backend.cosmetic.entity.CosmeticSlot;
import com.aisip.OnO.backend.cosmetic.support.CosmeticTestSupport;
import com.aisip.OnO.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 같은 사용자의 장착 요청이 동시에 들어올 때.
 *
 * <p>장착이 기대는 보장은 하나뿐이다. {@code user_cosmetic_loadout} 의 {@code (user_id, slot)}
 * 복합 기본키다. 이 키가 있으니 "없으면 INSERT 있으면 UPDATE" 를 애플리케이션에서 가르지 않고
 * upsert 한 문장으로 처리할 수 있고, 한 슬롯에 두 행이 생길 수 없다.
 *
 * <p>여기서 고정하는 것은 세 가지다. 중복 행이 생기지 않는다는 것, 동시 요청이 500 으로
 * 새어 나가지 않는다는 것, 그리고 여러 행을 건드리는 요청이 겹쳐도 교착으로 멈추지 않는다는 것이다.
 */
@DisplayName("동시성 - 꾸미기 장착")
class CosmeticEquipConcurrencyTest extends CosmeticTestSupport {

    private static final int THREAD_COUNT = 8;

    private User user;

    @BeforeEach
    void setUpUser() {
        user = fullyGrownUser();
    }

    @Nested
    @DisplayName("같은 슬롯")
    class SameSlot {

        @Test
        @DisplayName("같은 슬롯에 서로 다른 아이템을 8번 동시에 걸어도 행은 하나다")
        void concurrentEquipsLeaveOneRow() {
            List<String> hats = List.of(HAT_BEANIE, HAT_BUCKET, HAT_CROWN, HAT_GRADUATE);

            ConcurrentRunner.Outcome outcome = ConcurrentRunner.runConcurrently(
                    THREAD_COUNT,
                    index -> cosmeticService.equip(user.getId(), CosmeticSlot.HEAD, hats.get(index % hats.size())));

            assertThat(outcome.serverErrors())
                    .as("동시 장착이 500 으로 나가면 안 된다")
                    .isEmpty();
            assertThat(rowCount(CosmeticSlot.HEAD))
                    .as("(user_id, slot) 기본키가 한 슬롯 한 행을 보장한다")
                    .isEqualTo(1);
            assertThat(equippedOf(user.getId()).get(CosmeticSlot.HEAD))
                    .as("어느 쪽이 이기든 걸려 있는 것은 요청된 모자 중 하나여야 한다")
                    .isIn(hats);
        }

        @Test
        @DisplayName("같은 아이템을 8번 동시에 걸어도 결과는 한 번 건 것과 같다")
        void concurrentSameItemIsIdempotent() {
            ConcurrentRunner.Outcome outcome = ConcurrentRunner.runConcurrently(
                    THREAD_COUNT, () -> cosmeticService.equip(user.getId(), CosmeticSlot.HEAD, HAT_BEANIE));

            assertThat(outcome.serverErrors()).isEmpty();
            assertThat(rowCount(CosmeticSlot.HEAD)).isEqualTo(1);
            assertThat(equippedOf(user.getId())).containsEntry(CosmeticSlot.HEAD, HAT_BEANIE);
        }
    }

    @Nested
    @DisplayName("여러 슬롯")
    class DifferentSlots {

        @Test
        @DisplayName("서로 다른 슬롯을 동시에 걸면 둘 다 남는다")
        void concurrentEquipsOnDifferentSlotsBothSurvive() {
            List<SlotItem> targets = List.of(
                    new SlotItem(CosmeticSlot.HEAD, HAT_BEANIE),
                    new SlotItem(CosmeticSlot.BACKGROUND, BG_SPRING),
                    new SlotItem(CosmeticSlot.OUTFIT, OUTFIT_CARDIGAN),
                    new SlotItem(CosmeticSlot.FACE, GLASSES_ROUND));

            ConcurrentRunner.Outcome outcome = ConcurrentRunner.runConcurrently(
                    targets.size(),
                    index -> cosmeticService.equip(
                            user.getId(), targets.get(index).slot(), targets.get(index).itemKey()));

            assertThat(outcome.serverErrors())
                    .as("첫 장착마다 프리셋을 굳히는 쓰기가 겹치는 구간이다. 여기서 교착이 나면 500 이다")
                    .isEmpty();
            assertThat(equippedOf(user.getId()))
                    .containsEntry(CosmeticSlot.HEAD, HAT_BEANIE)
                    .containsEntry(CosmeticSlot.BACKGROUND, BG_SPRING)
                    .containsEntry(CosmeticSlot.OUTFIT, OUTFIT_CARDIGAN)
                    .containsEntry(CosmeticSlot.FACE, GLASSES_ROUND);
            assertThat(loadoutRowCount(user.getId()))
                    .as("프리셋이 여러 번 굳어도 슬롯 수를 넘는 행이 생기면 안 된다")
                    .isEqualTo(EQUIPPABLE_SLOT_COUNT);
        }

        @Test
        @DisplayName("세트 장착과 단일 장착이 겹쳐도 교착 없이 끝난다")
        void concurrentSetAndSingleEquip() {
            ConcurrentRunner.Outcome outcome = ConcurrentRunner.runConcurrently(
                    THREAD_COUNT,
                    index -> {
                        if (index % 2 == 0) {
                            cosmeticService.equipSet(user.getId(), GRADUATE_SET);
                        } else {
                            cosmeticService.equip(user.getId(), CosmeticSlot.HEAD, HAT_BEANIE);
                        }
                    });

            assertThat(outcome.serverErrors())
                    .as("두 요청이 같은 행들을 다른 순서로 잠그면 교착이 난다")
                    .isEmpty();
            assertThat(rowCount(CosmeticSlot.HEAD)).isEqualTo(1);
        }
    }

    private long rowCount(CosmeticSlot slot) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_cosmetic_loadout WHERE user_id = ? AND slot = ?",
                Long.class, user.getId(), slot.name());
        return count == null ? 0 : count;
    }

    private record SlotItem(CosmeticSlot slot, String itemKey) {
    }
}
