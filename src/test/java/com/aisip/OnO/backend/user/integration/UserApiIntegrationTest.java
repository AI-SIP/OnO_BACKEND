package com.aisip.OnO.backend.user.integration;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.user.dto.UserRegisterDto;
import com.aisip.OnO.backend.user.dto.UserResponseDto;
import com.aisip.OnO.backend.user.entity.User;
import com.aisip.OnO.backend.user.exception.UserErrorCase;
import com.aisip.OnO.backend.user.repository.UserRepository;
import com.aisip.OnO.backend.user.service.UserService;
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
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT) // 랜덤 포트로 애플리케이션 실행
@AutoConfigureMockMvc
public class UserApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Long userId;

    @BeforeEach
    void setUp() {
        // 유저 등록 (식별자 중복 피하려고 시간 기반 추가)
        String uniqueIdentifier = "testIdentifier_" + System.currentTimeMillis();
        UserRegisterDto registerDto = new UserRegisterDto(
                "test@example.com", "testUser", uniqueIdentifier, "MEMBER", null
        );
        userService.registerMemberUser(registerDto);
        User savedUser = userService.findUserEntityByIdentifier(uniqueIdentifier);
        userId = savedUser.getId();

        // 인증 설정
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        userId, null, List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))
                )
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("유저 정보 조회 - 성공")
    void getUserInfo() throws Exception {
        // When & Then
        MvcResult result = mockMvc.perform(get("/api/users")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("testUser"))
                .andExpect(jsonPath("$.data.email").value("test@example.com"))
                .andReturn();

        String json = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        System.out.println("==== 응답 결과 ====");
        System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(objectMapper.readTree(json)));
    }

    @Test
    @DisplayName("유저 정보 수정 - 성공")
    void updateUserInfo() throws Exception {
        // Given
        UserRegisterDto updateRequest = new UserRegisterDto("updated@example.com", "UpdatedUser", "updatedIdentifier", "MEMBER", null);

        // When & Then
        MvcResult result = mockMvc.perform(patch("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("사용자 정보 수정이 완료되었습니다."))
                .andReturn();

        String json = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        System.out.println("==== 응답 결과 ====");
        System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(objectMapper.readTree(json)));

        // 수정된 데이터 확인
        UserResponseDto userResponseDto = userService.findUser(userId);
        assertThat(userResponseDto.name()).isEqualTo("UpdatedUser");
        assertThat(userResponseDto.email()).isEqualTo("updated@example.com");
    }

    @Test
    @DisplayName("유저 삭제 - 성공")
    void deleteUserInfo() throws Exception {
        // When & Then
        MvcResult result = mockMvc.perform(delete("/api/users")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        String json = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        System.out.println("==== 응답 결과 ====");
        System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(objectMapper.readTree(json)));

        // 삭제 후, 다시 조회 시 예외 발생 여부 검증
        assertThatThrownBy(() -> userService.findUser(userId))
                .isInstanceOf(ApplicationException.class) // ✅ 특정 예외 발생 확인
                .hasMessageContaining(UserErrorCase.USER_NOT_FOUND.getMessage()); // 예외 메시지 검증
    }
}
