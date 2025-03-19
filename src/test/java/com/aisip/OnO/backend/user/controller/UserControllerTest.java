package com.aisip.OnO.backend.user.controller;

import com.aisip.OnO.backend.user.dto.UserRegisterDto;
import com.aisip.OnO.backend.user.dto.UserResponseDto;
import com.aisip.OnO.backend.user.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;


@ExtendWith(SpringExtension.class)
@SpringBootTest
@AutoConfigureMockMvc
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @Autowired
    private ObjectMapper objectMapper;

    private UserResponseDto mockUserResponse;

    @BeforeEach
    void setUp() {
        mockUserResponse = new UserResponseDto(1L, "testUser", "test@example.com", LocalDateTime.now(), LocalDateTime.now());
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    @DisplayName("사용자 정보 조회")
    void getUserInfo() throws Exception {
        // Given
        Long userId = 1L;
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of(new SimpleGrantedAuthority("ROLE_MEMBER")))
        );

        given(userService.findUser(userId)).willReturn(mockUserResponse);

        // When & Then
        mockMvc.perform(get("/api/user")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("testUser"))
                .andExpect(jsonPath("$.data.email").value("test@example.com"));
    }

    @Test
    @DisplayName("사용자 정보 수정")
    void updateUserInfo() throws Exception {
        // Given
        Long userId = 1L;
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of(new SimpleGrantedAuthority("ROLE_MEMBER")))
        );

        UserRegisterDto updateRequest = new UserRegisterDto("updated@example.com", "UpdatedUser", "updatedIdentifier", "USER", null);

        // When & Then
        mockMvc.perform(patch("/api/user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("사용자 정보 수정이 완료되었습니다."));
    }

    @Test
    @DisplayName("사용자 계정 삭제")
    void deleteUserInfo() throws Exception {
        // Given
        Long userId = 1L;
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of(new SimpleGrantedAuthority("ROLE_MEMBER")))
        );

        // When & Then
        mockMvc.perform(delete("/api/user")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        // userService.deleteUserById(userId)가 1번 호출되었는지 확인
        Mockito.verify(userService, Mockito.times(1)).deleteUserById(userId);
    }
}