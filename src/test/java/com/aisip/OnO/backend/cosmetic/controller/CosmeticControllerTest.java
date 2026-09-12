package com.aisip.OnO.backend.cosmetic.controller;

import com.aisip.OnO.backend.cosmetic.entity.CosmeticSlot;
import com.aisip.OnO.backend.cosmetic.exception.CosmeticErrorCase;
import com.aisip.OnO.backend.cosmetic.support.CosmeticTestSupport;
import com.aisip.OnO.backend.user.entity.User;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 프론트와 맞춘 응답 형태를 고정한다. 필드 이름이나 묶음 구조가 바뀌면 앱이 그대로 깨진다.
 */
@DisplayName("꾸미기 API 계약")
class CosmeticControllerTest extends CosmeticTestSupport {

    @Test
    @DisplayName("GET /api/cosmetics - 본체·슬롯·아이템·장착 상태를 한 번에 내려준다")
    void getCosmetics() throws Exception {
        // 출석 2, 문제 복습 4. 봄 배경(출석 2)과 비니(문제 복습 4)가 열려 있다.
        User user = setLevels(fixtures.createUser(), 1L, 2L, 1L, 4L, 1L);
        authenticateAs(user.getId());

        mockMvc.perform(get("/api/cosmetics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.baseImageUrl").value("assets/Cosmetic/BASE.png"))
                .andExpect(jsonPath("$.data.baseLayerOrder").value(300))
                .andExpect(jsonPath("$.data.slots.length()").value(11))
                .andExpect(jsonPath("$.data.slots[0].slot").value("BACKGROUND"))
                .andExpect(jsonPath("$.data.slots[0].layerOrder").value(100))
                .andExpect(jsonPath("$.data.slots[0].nameKo").value("배경"))
                .andExpect(jsonPath("$.data.slots[0].composited").value(true))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items.length()").value(SEEDED_ITEM_COUNT))
                .andExpect(jsonPath("$.data.equipped.HEAD").value(HAT_BEANIE))
                .andExpect(jsonPath("$.data.equipped.BACKGROUND").value(BG_SPRING));
    }

    @Test
    @DisplayName("GET /api/cosmetics - slots 에 등짐(200)과 효과(900)가 빠짐없이 들어 있다")
    void slotsIncludeBackAndEffect() throws Exception {
        User user = fullyGrownUser();
        authenticateAs(user.getId());

        String back = "$.data.slots[?(@.slot == 'BACK')]";
        String bag = "$.data.slots[?(@.slot == 'BAG')]";
        String effect = "$.data.slots[?(@.slot == 'EFFECT')]";
        mockMvc.perform(get("/api/cosmetics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(back + ".layerOrder").value(Matchers.contains(200)))
                .andExpect(jsonPath(back + ".nameKo").value(Matchers.contains("등짐")))
                .andExpect(jsonPath(bag + ".layerOrder").value(Matchers.contains(450)))
                .andExpect(jsonPath(effect + ".layerOrder").value(Matchers.contains(900)));
    }

    @Test
    @DisplayName("GET /api/cosmetics - 프레임은 자리만 내려가고 자동으로 걸리지는 않는다")
    void frameSlotIsNotComposited() throws Exception {
        User user = fullyGrownUser();
        authenticateAs(user.getId());

        String frame = "$.data.slots[?(@.slot == 'FRAME')]";
        mockMvc.perform(get("/api/cosmetics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(frame + ".layerOrder").value(Matchers.contains(1000)))
                .andExpect(jsonPath(frame + ".nameKo").value(Matchers.contains("프레임")))
                .andExpect(jsonPath(frame + ".composited")
                        .value(Matchers.contains(false)))
                .andExpect(jsonPath("$.data.equipped.FRAME")
                        .doesNotExist());
    }

    @Test
    @DisplayName("GET /api/cosmetics - 프레임 아이템은 SVG 경로로 나간다")
    void frameItemShape() throws Exception {
        User user = fullyGrownUser();
        authenticateAs(user.getId());

        String frame = "$.data.items[?(@.itemKey == '" + FRAME_SPRING + "')]";
        mockMvc.perform(get("/api/cosmetics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(frame + ".slot").value(Matchers.contains("FRAME")))
                .andExpect(jsonPath(frame + ".nameKo").value(Matchers.contains("봄 프레임")))
                .andExpect(jsonPath(frame + ".imageUrl")
                        .value(Matchers.contains("assets/ProfileFrame/frame_spring.svg")))
                .andExpect(jsonPath(frame + ".requiredLevel").value(Matchers.contains(3)))
                .andExpect(jsonPath(frame + ".requiredAbility").value(Matchers.contains("ATTENDANCE")))
                .andExpect(jsonPath(frame + ".fullBody").value(Matchers.contains(false)))
                .andExpect(jsonPath(frame + ".owned").value(Matchers.contains(true)));
    }

    @Test
    @DisplayName("PUT /api/cosmetics/equip - 프레임도 다른 자리처럼 걸린다")
    void equipFrame() throws Exception {
        User user = fullyGrownUser();
        authenticateAs(user.getId());

        mockMvc.perform(put("/api/cosmetics/equip")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("FRAME", FRAME_SPRING)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.equipped.FRAME").value(FRAME_SPRING))
                .andExpect(jsonPath("$.data.equipped.BACKGROUND").value(BG_SPACE));
    }

    @Test
    @DisplayName("GET /api/cosmetics - 아이템 한 건의 필드가 계약대로다")
    void itemShape() throws Exception {
        User user = fullyGrownUser();
        authenticateAs(user.getId());

        String glasses = "$.data.items[?(@.itemKey == '" + GLASSES_ROUND + "')]";
        mockMvc.perform(get("/api/cosmetics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(glasses + ".slot").value(Matchers.contains("FACE")))
                .andExpect(jsonPath(glasses + ".nameKo").value(Matchers.contains("동그란 안경")))
                .andExpect(jsonPath(glasses + ".imageUrl")
                        .value(Matchers.contains("assets/Cosmetic/glasses_round.png")))
                .andExpect(jsonPath(glasses + ".requiredLevel").value(Matchers.contains(2)))
                .andExpect(jsonPath(glasses + ".requiredAbility").value(Matchers.contains("PROBLEM_PRACTICE")))
                .andExpect(jsonPath(glasses + ".fullBody").value(Matchers.contains(false)))
                .andExpect(jsonPath(glasses + ".setId").value(Matchers.contains(Matchers.nullValue())))
                .andExpect(jsonPath(glasses + ".setNameKo").value(Matchers.contains(Matchers.nullValue())))
                .andExpect(jsonPath(glasses + ".conflictsWith").value(Matchers.contains(Matchers.empty())))
                .andExpect(jsonPath(glasses + ".owned").value(Matchers.contains(true)));
    }

    @Test
    @DisplayName("GET /api/cosmetics - 총 학습 레벨로 열리는 아이템은 requiredAbility 가 null 이다")
    void totalLevelItemHasNullAbility() throws Exception {
        User user = fullyGrownUser();
        authenticateAs(user.getId());

        String sprout = "$.data.items[?(@.itemKey == '" + HEADBAND_SPROUT + "')]";
        mockMvc.perform(get("/api/cosmetics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(sprout + ".requiredAbility").value(Matchers.contains(Matchers.nullValue())))
                .andExpect(jsonPath(sprout + ".requiredLevel").value(Matchers.contains(2)));
    }

    @Test
    @DisplayName("GET /api/cosmetics - 전신 의상과 세트 이름이 실려 나간다")
    void fullBodyAndSetName() throws Exception {
        User user = fullyGrownUser();
        authenticateAs(user.getId());

        String gown = "$.data.items[?(@.itemKey == '" + OUTFIT_GRADUATE + "')]";
        mockMvc.perform(get("/api/cosmetics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(gown + ".fullBody").value(Matchers.contains(true)))
                .andExpect(jsonPath(gown + ".setId").value(Matchers.contains(GRADUATE_SET)))
                .andExpect(jsonPath(gown + ".setNameKo").value(Matchers.contains(GRADUATE_SET_NAME)));
    }

    @Test
    @DisplayName("GET /api/cosmetics - 비로그인 요청은 401 이다")
    void requiresAuthentication() throws Exception {
        clearAuthentication();

        mockMvc.perform(get("/api/cosmetics"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PUT /api/cosmetics/equip - 갱신된 장착 상태 전체와 벗겨진 슬롯을 돌려준다")
    void equip() throws Exception {
        User user = fullyGrownUser();
        authenticateAs(user.getId());

        mockMvc.perform(put("/api/cosmetics/equip")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("HEAD", HAT_BEANIE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.equipped.HEAD").value(HAT_BEANIE))
                .andExpect(jsonPath("$.data.equipped.BACKGROUND").value(BG_SPACE))
                .andExpect(jsonPath("$.data.unequippedSlots").isArray())
                .andExpect(jsonPath("$.data.unequippedSlots.length()").value(0));
    }

    @Test
    @DisplayName("PUT /api/cosmetics/equip - itemKey 가 null 이면 그 슬롯만 벗는다")
    void unequip() throws Exception {
        User user = fullyGrownUser();
        authenticateAs(user.getId());

        mockMvc.perform(put("/api/cosmetics/equip")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("HEAD", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.equipped.HEAD").doesNotExist())
                .andExpect(jsonPath("$.data.equipped.BACKGROUND").value(BG_SPACE));
    }

    @Test
    @DisplayName("PUT /api/cosmetics/equip - 앞가방과 등짐은 따로 걸린다")
    void equipBagAndBack() throws Exception {
        User user = fullyGrownUser();
        authenticateAs(user.getId());

        mockMvc.perform(put("/api/cosmetics/equip")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("BACK", BACK_BACKPACK_NAVY)))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/cosmetics/equip")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("BAG", BAG_MINI_BACKPACK)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.equipped.BACK").value(BACK_BACKPACK_NAVY))
                .andExpect(jsonPath("$.data.equipped.BAG").value(BAG_MINI_BACKPACK));
    }

    @Test
    @DisplayName("PUT /api/cosmetics/equip - 그 능력치 레벨이 모자라면 400 이다")
    void rejectsUnownedItem() throws Exception {
        // 총 학습은 20 이지만 문제 복습이 3 이라 비니(문제 복습 4)는 잠겨 있다.
        User user = setLevels(fixtures.createUser(), 20L, 15L, 15L, 3L, 15L);
        authenticateAs(user.getId());

        mockMvc.perform(put("/api/cosmetics/equip")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("HEAD", HAT_BEANIE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode")
                        .value(CosmeticErrorCase.COSMETIC_ITEM_NOT_OWNED.getErrorCode()))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("PUT /api/cosmetics/equip - 없는 슬롯 이름은 400 이다")
    void rejectsUnknownSlot() throws Exception {
        User user = fullyGrownUser();
        authenticateAs(user.getId());

        mockMvc.perform(put("/api/cosmetics/equip")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slot\":\"WINGS\",\"itemKey\":null}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /api/cosmetics/equip - 슬롯이 빠지면 400 이다")
    void rejectsMissingSlot() throws Exception {
        User user = fullyGrownUser();
        authenticateAs(user.getId());

        mockMvc.perform(put("/api/cosmetics/equip")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemKey\":\"" + HAT_BEANIE + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /api/cosmetics/equip-set - 세트가 각 슬롯에 한 번에 걸린다")
    void equipSet() throws Exception {
        User user = fullyGrownUser();
        authenticateAs(user.getId());

        mockMvc.perform(put("/api/cosmetics/equip-set")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"setId\":\"" + GRADUATE_SET + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.equipped.HEAD").value(HAT_GRADUATE))
                .andExpect(jsonPath("$.data.equipped.OUTFIT").value(OUTFIT_GRADUATE))
                .andExpect(jsonPath("$.data.equipped.HAND").value(PROP_DIPLOMA));
    }

    @Test
    @DisplayName("PUT /api/cosmetics/equip-set - 하나라도 미보유면 400 이다")
    void rejectsPartiallyOwnedSet() throws Exception {
        User user = setLevels(fixtures.createUser(), 19L, 15L, 15L, 15L, 15L);
        authenticateAs(user.getId());

        mockMvc.perform(put("/api/cosmetics/equip-set")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"setId\":\"" + GRADUATE_SET + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode")
                        .value(CosmeticErrorCase.COSMETIC_ITEM_NOT_OWNED.getErrorCode()));
    }

    @Test
    @DisplayName("PUT /api/cosmetics/equip - 비로그인 요청은 401 이다")
    void equipRequiresAuthentication() throws Exception {
        clearAuthentication();

        mockMvc.perform(put("/api/cosmetics/equip")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("HEAD", HAT_BEANIE)))
                .andExpect(status().isUnauthorized());
    }

    /** {@code itemKey} 가 null 인 본문을 만들어야 해서 Map 으로 직렬화한다. */
    private String body(String slot, String itemKey) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("slot", slot);
        body.put("itemKey", itemKey);
        return objectMapper.writeValueAsString(body);
    }

    /** 슬롯 이름이 enum 상수와 그대로 맞는지. 응답의 키가 곧 이 이름이다. */
    @Test
    @DisplayName("슬롯 이름은 enum 상수 이름 그대로 나간다")
    void slotNamesAreEnumNames() throws Exception {
        User user = fullyGrownUser();
        authenticateAs(user.getId());

        for (CosmeticSlot slot : CosmeticSlot.equippableSlots()) {
            mockMvc.perform(get("/api/cosmetics"))
                    .andExpect(jsonPath("$.data.slots[?(@.slot == '" + slot.name() + "')]")
                            .exists());
        }
    }
}
