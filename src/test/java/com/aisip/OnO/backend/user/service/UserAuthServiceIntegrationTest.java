package com.aisip.OnO.backend.user.service;

import com.aisip.OnO.backend.auth.service.UserAuthService;
import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.user.dto.UserRegisterDto;
import com.aisip.OnO.backend.user.dto.UserResponseDto;
import com.aisip.OnO.backend.user.entity.User;
import com.aisip.OnO.backend.user.exception.UserErrorCase;
import com.aisip.OnO.backend.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT) // 랜덤 포트로 애플리케이션 실행
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class) // 테스트 순서 지정 가능
public class UserAuthServiceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserAuthService userAuthService;

    @Autowired
    private UserService userService;

    @Autowired
    private ObjectMapper objectMapper;

    private static Long userId; // 생성된 유저 ID 저장

    @BeforeEach
    void setUp() {

    }

    @Test
    @Order(1)
    @DisplayName(" 유저 회원가입 - 성공")
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
        User savedUser = userService.findUserEntityByIdentifier("testIdentifier");
        assertThat(savedUser).isNotNull();
        assertThat(savedUser.getId()).isNotNull();
        userId = savedUser.getId(); // 이후 테스트를 위해 userId 저장
    }

    @Test
    @Order(2)
    @DisplayName("유저 정보 조회 - 성공")
    void getUserInfo() throws Exception {
        // Given (유저 데이터 미리 저장)
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of(new SimpleGrantedAuthority("ROLE_MEMBER")))
        );

        // When & Then
        mockMvc.perform(get("/api/user")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("testUser"))
                .andExpect(jsonPath("$.data.email").value("test@example.com"));
    }

    @Test
    @Order(3)
    @DisplayName("유저 정보 수정 - 성공")
    void updateUserInfo() throws Exception {
        // Given
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of(new SimpleGrantedAuthority("ROLE_MEMBER")))
        );

        UserRegisterDto updateRequest = new UserRegisterDto("updated@example.com", "UpdatedUser", "updatedIdentifier", "MEMBER", null);

        // When & Then
        mockMvc.perform(patch("/api/user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("사용자 정보 수정이 완료되었습니다."));

        // 수정된 데이터 확인
        UserResponseDto userResponseDto = userService.findUser(userId);
        assertThat(userResponseDto.name()).isEqualTo("UpdatedUser");
        assertThat(userResponseDto.email()).isEqualTo("updated@example.com");
    }

    @Test
    @Order(4)
    @DisplayName("유저 삭제 - 성공")
    void deleteUserInfo() throws Exception {
        // Given
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of(new SimpleGrantedAuthority("ROLE_MEMBER")))
        );

        // When & Then
        mockMvc.perform(delete("/api/user")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        // 삭제 후, 다시 조회 시 예외 발생 여부 검증
        assertThatThrownBy(() -> userService.findUser(userId))
                .isInstanceOf(ApplicationException.class) // ✅ 특정 예외 발생 확인
                .hasMessageContaining(UserErrorCase.USER_NOT_FOUND.getMessage()); // 예외 메시지 검증
    }
}
