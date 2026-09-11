package com.aisip.OnO.backend.cosmetic.controller;

import com.aisip.OnO.backend.cosmetic.entity.CosmeticSlot;
import com.aisip.OnO.backend.cosmetic.exception.CosmeticErrorCase;
import com.aisip.OnO.backend.cosmetic.support.CosmeticTestSupport;
import com.aisip.OnO.backend.user.entity.User;
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
        User user = userAtLevel(6);
        authenticateAs(user.getId());

        mockMvc.perform(get("/api/cosmetics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.baseImageUrl").value("assets/Cosmetic/BASE.png"))
                .andExpect(jsonPath("$.data.baseLayerOrder").value(300))
                .andExpect(jsonPath("$.data.slots.length()").value(9))
                .andExpect(jsonPath("$.data.slots[0].slot").value("BACKGROUND"))
                .andExpect(jsonPath("$.data.slots[0].layerOrder").value(100))
                .andExpect(jsonPath("$.data.slots[0].nameKo").value("배경"))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.equipped.HEAD").value(HAT_BEANIE))
                .andExpect(jsonPath("$.data.equipped.BACKGROUND").value(BG_SPRING));
    }

    @Test
    @DisplayName("GET /api/cosmetics - 아이템 한 건의 필드가 계약대로다")
    void itemShape() throws Exception {
        User user = userAtLevel(6);
        authenticateAs(user.getId());

        // 배경(100) 다음이 가방(200), 그다음 옷(400)... 머리(700)의 첫 아이템이 새싹 머리띠(레벨 2)다.
        String beanie = "$.data.items[?(@.itemKey == '" + HAT_BEANIE + "')]";
        mockMvc.perform(get("/api/cosmetics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(beanie + ".slot").value("HEAD"))
                .andExpect(jsonPath(beanie + ".nameKo").value("비니"))
                .andExpect(jsonPath(beanie + ".imageUrl").value("assets/Cosmetic/hat_beanie.png"))
                .andExpect(jsonPath(beanie + ".requiredLevel").value(6))
                .andExpect(jsonPath(beanie + ".setId").value(org.hamcrest.Matchers.contains(
                        org.hamcrest.Matchers.nullValue())))
                .andExpect(jsonPath(beanie + ".conflictsWith").value(org.hamcrest.Matchers.contains(
                        org.hamcrest.Matchers.empty())))
                .andExpect(jsonPath(beanie + ".owned").value(true));
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
        User user = userAtLevel(15);
        authenticateAs(user.getId());

        mockMvc.perform(put("/api/cosmetics/equip")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("HEAD", HAT_BEANIE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.equipped.HEAD").value(HAT_BEANIE))
                .andExpect(jsonPath("$.data.equipped.BACKGROUND").value(BG_NIGHT))
                .andExpect(jsonPath("$.data.unequippedSlots").isArray())
                .andExpect(jsonPath("$.data.unequippedSlots.length()").value(0));
    }

    @Test
    @DisplayName("PUT /api/cosmetics/equip - itemKey 가 null 이면 그 슬롯만 벗는다")
    void unequip() throws Exception {
        User user = userAtLevel(15);
        authenticateAs(user.getId());

        mockMvc.perform(put("/api/cosmetics/equip")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("HEAD", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.equipped.HEAD").doesNotExist())
                .andExpect(jsonPath("$.data.equipped.BACKGROUND").value(BG_NIGHT));
    }

    @Test
    @DisplayName("PUT /api/cosmetics/equip - 보유하지 않은 아이템은 400 이다")
    void rejectsUnownedItem() throws Exception {
        User user = userAtLevel(5);
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
        User user = userAtLevel(15);
        authenticateAs(user.getId());

        mockMvc.perform(put("/api/cosmetics/equip")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slot\":\"WINGS\",\"itemKey\":null}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /api/cosmetics/equip - 슬롯이 빠지면 400 이다")
    void rejectsMissingSlot() throws Exception {
        User user = userAtLevel(15);
        authenticateAs(user.getId());

        mockMvc.perform(put("/api/cosmetics/equip")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemKey\":\"" + HAT_BEANIE + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /api/cosmetics/equip-set - 세트가 각 슬롯에 한 번에 걸린다")
    void equipSet() throws Exception {
        User user = userAtLevel(15);
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
        User user = userAtLevel(14);
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
        User user = userAtLevel(15);
        authenticateAs(user.getId());

        for (CosmeticSlot slot : CosmeticSlot.equippableSlots()) {
            mockMvc.perform(get("/api/cosmetics"))
                    .andExpect(jsonPath("$.data.slots[?(@.slot == '" + slot.name() + "')]")
                            .exists());
        }
    }
}
