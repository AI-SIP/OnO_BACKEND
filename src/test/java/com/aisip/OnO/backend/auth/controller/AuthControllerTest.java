package com.aisip.OnO.backend.auth.controller;

import com.aisip.OnO.backend.auth.dto.TokenRequestDto;
import com.aisip.OnO.backend.auth.dto.TokenResponseDto;
import com.aisip.OnO.backend.auth.service.UserAuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserAuthService userAuthService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("게스트 유저 회원가입")
    void signUpGuest() throws Exception {
        // Given
        TokenResponseDto mockTokenResponse = new TokenResponseDto("mockAccessToken", "mockRefreshToken");
        given(userAuthService.signUpGuestUser()).willReturn(mockTokenResponse);

        // When & Then
        mockMvc.perform(post("/api/auth/signup/guest")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("mockAccessToken"))
                .andExpect(jsonPath("$.data.refreshToken").value("mockRefreshToken"));
    }

    @Test
    @DisplayName("멤버 유저 회원가입")
    void signUpMember() throws Exception {
        // Given
        TokenResponseDto mockTokenResponse = new TokenResponseDto("mockAccessToken", "mockRefreshToken");
        given(userAuthService.signUpMemberUser(any())).willReturn(mockTokenResponse);

        // When & Then
        mockMvc.perform(post("/api/auth/signup/member")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new com.aisip.OnO.backend.user.dto.UserRegisterDto(
                                "test@example.com", "testUser", "testIdentifier", "MEMBER", null
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("mockAccessToken"))
                .andExpect(jsonPath("$.data.refreshToken").value("mockRefreshToken"));
    }

    @Test
    @DisplayName("토큰 갱신")
    void refreshToken() throws Exception {
        // Given
        TokenResponseDto mockTokenResponse = new TokenResponseDto("newMockAccessToken", "mockRefreshToken");
        given(userAuthService.refreshAccessToken(any())).willReturn(mockTokenResponse);

        TokenRequestDto requestDto = new TokenRequestDto("mockAccessToken", "mockRefreshToken");

        // When & Then
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("newMockAccessToken"))
                .andExpect(jsonPath("$.data.refreshToken").value("mockRefreshToken"));

        // Verify userAuthService 호출 검증
        Mockito.verify(userAuthService, Mockito.times(1)).refreshAccessToken(any());
    }
}