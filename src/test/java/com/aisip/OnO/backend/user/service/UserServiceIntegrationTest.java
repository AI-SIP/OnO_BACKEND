package com.aisip.OnO.backend.user.service;

import com.aisip.OnO.backend.user.dto.UserRegisterDto;
import com.aisip.OnO.backend.user.entity.User;
import com.aisip.OnO.backend.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT) // 랜덤 포트로 애플리케이션 실행
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class) // 테스트 순서 지정 가능
public class UserServiceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private static Long userId; // 생성된 유저 ID 저장

    @BeforeEach
    void setUp() {
        userRepository.deleteAll(); // DB 초기화
    }

    @Test
    @Order(1)
    @DisplayName(" 유저 회원가입 - 성공")
    @WithMockUser(username = "1", roles = "ROLE_MEMBER")
    void registerUser() throws Exception {
        // Given
        UserRegisterDto requestDto = new UserRegisterDto("test@example.com", "testUser", "testIdentifier", "MEMBER", null);

        // When & Then
        mockMvc.perform(post("/api/auth/signup/member")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").exists()) // 토큰 발급 확인
                .andExpect(jsonPath("$.data.refreshToken").exists());

        // 저장된 유저 확인
        Optional<User> savedUser = userRepository.findByIdentifier("testIdentifier");
        assertThat(savedUser).isPresent();
        userId = savedUser.get().getId(); // 이후 테스트를 위해 userId 저장
    }

}
