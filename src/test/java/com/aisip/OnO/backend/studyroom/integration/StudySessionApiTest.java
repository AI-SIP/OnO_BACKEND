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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StudySessionApiTest {

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
    void nonMemberCannotStartStudySession() throws Exception {
        User host = saveUser("방장", "session-nonmember-host");
        User outsider = saveUser("외부", "session-nonmember-outsider");
        authenticate(host.getId());
        Long roomId = createRoom("세션 권한 테스트방");

        authenticate(outsider.getId());
        mockMvc.perform(post("/api/study-room/{roomId}/sessions", roomId)
                        .with(auth()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(10002));
    }

    @Test
    void cannotEndOtherUsersStudySession() throws Exception {
        User host = saveUser("방장", "session-end-host");
        User member = saveUser("멤버", "session-end-member");
        authenticate(host.getId());
        Long roomId = createRoom("세션 종료 테스트방");
        String inviteCode = issueInviteCode(roomId);
        authenticate(member.getId());
        join(inviteCode);

        // host가 세션 시작
        authenticate(host.getId());
        MvcResult startResult = mockMvc.perform(post("/api/study-room/{roomId}/sessions", roomId)
                        .with(auth()))
                .andExpect(status().isCreated())
                .andReturn();
        Number sessionIdValue = JsonPath.read(startResult.getResponse().getContentAsString(), "$.data.sessionId");
        Long sessionId = sessionIdValue.longValue();

        // member가 host 세션 종료 시도 → SESSION_NOT_FOUND (10012): findByIdAndRoomIdAndUserId 미일치
        authenticate(member.getId());
        mockMvc.perform(patch("/api/study-room/{roomId}/sessions/{sessionId}/end", roomId, sessionId)
                        .with(auth()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value(10012));
    }

    // ========== 헬퍼 ==========

    private User saveUser(String name, String emailPrefix) {
        return userRepository.save(RandomUserGenerator.createRandomUser("GOOGLE", name, emailPrefix));
    }

    private void authenticate(Long userId) {
        authenticatedUserId = userId;
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of(new SimpleGrantedAuthority("ROLE_MEMBER")))
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
        MvcResult result = mockMvc.perform(post("/api/study-room/{roomId}/invite", roomId)
                        .with(auth()))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.code");
    }

    private void join(String inviteCode) throws Exception {
        mockMvc.perform(post("/api/study-room/join")
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new StudyRoomJoinRequest(inviteCode))))
                .andExpect(status().isOk());
    }
}
