package com.aisip.OnO.backend.studyroom.integration;

import com.aisip.OnO.backend.studyroom.dto.StudyRoomDtos.*;
import com.aisip.OnO.backend.user.entity.User;
import com.aisip.OnO.backend.user.repository.UserRepository;
import com.aisip.OnO.backend.util.RandomUserGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StudyRoomInviteApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    private Long authenticatedUserId;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void inviteCodeIsReusedWhenNotExpired() throws Exception {
        User host = saveUser("방장", "invite-reuse-host");
        authenticate(host.getId());
        Long roomId = createRoom("코드 재사용 테스트방");

        String code1 = issueInviteCode(roomId);
        String code2 = issueInviteCode(roomId);

        // 아직 만료되지 않았으므로 동일 코드 반환
        assertThat(code2).isEqualTo(code1);
    }

    @Test
    void invalidFormatInviteCodeIsRejected() throws Exception {
        User user = saveUser("사용자", "invite-invalid-user");
        authenticate(user.getId());

        // 6자리 숫자가 아닌 코드 "abc" → 10006
        mockMvc.perform(post("/api/study-room/join")
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new StudyRoomJoinRequest("abc"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(10006));

        // 7자리 숫자 코드 "1234567" → 10006
        mockMvc.perform(post("/api/study-room/join")
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new StudyRoomJoinRequest("1234567"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(10006));

        // null 코드 → 10006
        mockMvc.perform(post("/api/study-room/join")
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new StudyRoomJoinRequest(null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(10006));
    }

    @Test
    void alreadyMemberCannotJoinRoom() throws Exception {
        User host = saveUser("방장", "invite-already-member-host");
        authenticate(host.getId());
        Long roomId = createRoom("중복 참여 테스트방");
        String code = issueInviteCode(roomId);

        // host가 자신의 방에 초대코드로 재가입 시도 → ALREADY_MEMBER (10008)
        mockMvc.perform(post("/api/study-room/join")
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new StudyRoomJoinRequest(code))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value(10008));
    }

    @Test
    void nonExistentValidFormatCodeIsRejected() throws Exception {
        User user = saveUser("사용자", "invite-nonexist-user");
        authenticate(user.getId());

        // 형식은 맞지만 DB에 없는 코드 → INVITE_CODE_INVALID (10006)
        mockMvc.perform(post("/api/study-room/join")
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new StudyRoomJoinRequest("000000"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(10006));
    }

    // ========== 헬퍼 ==========

    private User saveUser(String name, String emailPrefix) {
        return userRepository.save(RandomUserGenerator.createRandomUser("GOOGLE", name, emailPrefix));
    }

    private void authenticate(Long userId) {
        authenticatedUserId = userId;
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        userId, null, List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))
                )
        );
    }

    private RequestPostProcessor auth() {
        return authentication(new UsernamePasswordAuthenticationToken(
                authenticatedUserId, null, List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))
        ));
    }

    private Long createRoom(String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/study-room")
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new StudyRoomCreateRequest(name))))
                .andExpect(status().isCreated())
                .andReturn();
        Number value = JsonPath.read(result.getResponse().getContentAsString(), "$.data.roomId");
        return value.longValue();
    }

    private String issueInviteCode(Long roomId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/study-room/{roomId}/invite", roomId).with(auth()))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.code");
    }
}
