package com.aisip.OnO.backend.auth.controller;

import com.aisip.OnO.backend.auth.exception.AuthErrorCase;
import com.aisip.OnO.backend.support.IntegrationTestSupport;
import com.aisip.OnO.backend.util.fcm.dto.FcmTokenRequestDto;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 탈퇴한 계정에게 이미 나간 액세스 토큰을 더 이상 받아주지 않는지 확인한다.
 *
 * <p>액세스 토큰은 서명과 만료만으로 통과하므로, 탈퇴해도 만료 전(최대 30분)까지는 살아 있다.
 * 사용자 존재를 확인하는 경로는 404 로 떨어지지만, 확인하지 않는 {@code POST /api/fcm/token} 은
 * 200 으로 성공해 탈퇴 때 지운 {@code fcm_token} 행이 되살아났다. 그러면 그 기기를 이어 쓰는 사람에게
 * 탈퇴한 계정 앞으로 가는 알림이 뜬다. (#300, #271 이 막으려던 상황과 같은 결과)
 *
 * <p>여기서는 실제 {@code Authorization} 헤더로 요청을 보내 필터를 그대로 태운다.
 * 인증 컨텍스트를 직접 세우는 테스트는 이 경로를 지나지 않아 회귀를 잡지 못한다.
 */
@DisplayName("탈퇴한 계정의 액세스 토큰")
class DeletedUserAccessTokenApiTest extends IntegrationTestSupport {

    private static final String FCM_TOKEN_API = "/api/fcm/token";

    private JsonNode dataOf(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
    }

    /** 게스트로 가입하고 "Bearer " 를 포함한 액세스 토큰을 돌려준다. */
    private String signUpGuestAndGetAccessToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/signup/guest"))
                .andExpect(status().isOk())
                .andReturn();
        return dataOf(result).path("accessToken").asText();
    }

    private ResultActions registerFcmToken(String authorizationHeader, String fcmToken) throws Exception {
        return mockMvc.perform(post(FCM_TOKEN_API)
                .header("Authorization", authorizationHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new FcmTokenRequestDto(fcmToken))));
    }

    private void withdraw(String authorizationHeader) throws Exception {
        mockMvc.perform(delete("/api/users").header("Authorization", authorizationHeader))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("탈퇴 후 남은 토큰으로 FCM 토큰을 등록하면 401 + 1009 로 거절한다")
    void rejectsFcmTokenRegistrationAfterWithdrawal() throws Exception {
        String accessToken = signUpGuestAndGetAccessToken();
        withdraw(accessToken);

        registerFcmToken(accessToken, "fcm-token-after-withdrawal")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value(AuthErrorCase.INVALID_ACCESS_TOKEN.getErrorCode()));

        verify(fcmService, never()).registerToken(any(), any());
    }

    @Test
    @DisplayName("탈퇴 후 남은 토큰은 사용자 조회도 401 로 막는다")
    void rejectsUserLookupAfterWithdrawal() throws Exception {
        String accessToken = signUpGuestAndGetAccessToken();
        withdraw(accessToken);

        mockMvc.perform(get("/api/users").header("Authorization", accessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value(AuthErrorCase.INVALID_ACCESS_TOKEN.getErrorCode()));
    }

    @Test
    @DisplayName("탈퇴하지 않은 사용자는 FCM 토큰 등록이 그대로 된다")
    void allowsFcmTokenRegistrationForLivingUser() throws Exception {
        String accessToken = signUpGuestAndGetAccessToken();

        registerFcmToken(accessToken, "fcm-token-living-user")
                .andExpect(status().isOk());

        verify(fcmService).registerToken(eq(new FcmTokenRequestDto("fcm-token-living-user")), any());
    }

    @Test
    @DisplayName("남이 탈퇴해도 내 토큰은 영향을 받지 않는다")
    void withdrawalOfAnotherUserDoesNotAffectMe() throws Exception {
        String myAccessToken = signUpGuestAndGetAccessToken();
        String otherAccessToken = signUpGuestAndGetAccessToken();

        withdraw(otherAccessToken);

        registerFcmToken(myAccessToken, "fcm-token-of-mine")
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/users").header("Authorization", myAccessToken))
                .andExpect(status().isOk());
    }
}
