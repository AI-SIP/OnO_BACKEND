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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StudyRoomMemberManagementApiTest {

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

    // ========== 강퇴 권한 ==========

    @Test
    void hostCanKickMemberAndMemberCannotKick() throws Exception {
        User host = saveUser("방장", "mgmt-kick-host");
        User member = saveUser("멤버", "mgmt-kick-member");
        authenticate(host.getId());
        Long roomId = createRoom("강퇴 테스트방");
        String code = issueInviteCode(roomId);
        authenticate(member.getId());
        join(code);

        // member가 host 강퇴 시도 → 방장만 가능 (10003)
        mockMvc.perform(delete("/api/study-room/{roomId}/members/{memberId}", roomId, host.getId())
                        .with(auth()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(10003));

        // host가 member 강퇴 → 성공
        authenticate(host.getId());
        mockMvc.perform(delete("/api/study-room/{roomId}/members/{memberId}", roomId, member.getId())
                        .with(auth()))
                .andExpect(status().isOk());

        // 방 조회 → memberCount=1
        mockMvc.perform(get("/api/study-room/{roomId}", roomId).with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memberCount").value(1));
    }

    @Test
    void hostCannotKickSelf() throws Exception {
        User host = saveUser("방장", "mgmt-selfkick-host");
        authenticate(host.getId());
        Long roomId = createRoom("자기 강퇴 테스트방");

        // host가 자신을 강퇴 시도 → host 역할이라 막힘 (10003)
        mockMvc.perform(delete("/api/study-room/{roomId}/members/{memberId}", roomId, host.getId())
                        .with(auth()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(10003));
    }

    // ========== 방 삭제 권한 ==========

    @Test
    void hostCanDeleteRoomAndMemberCannot() throws Exception {
        // 시나리오 A: 방에 속한 member가 삭제 시도 → 방장만 가능 (10003)
        User host = saveUser("방장", "mgmt-delete-host");
        User member = saveUser("멤버", "mgmt-delete-member");
        authenticate(host.getId());
        Long roomIdWithMember = createRoom("삭제 권한 테스트방");
        String code = issueInviteCode(roomIdWithMember);
        authenticate(member.getId());
        join(code);

        mockMvc.perform(delete("/api/study-room/{roomId}", roomIdWithMember).with(auth()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(10003));

        // 시나리오 B: host 단독 방(초대코드 없음) 삭제 → 성공
        // 초대코드 FK 제약이 없는 방에서 검증
        User host2 = saveUser("방장2", "mgmt-delete-host2");
        authenticate(host2.getId());
        Long roomIdAlone = createRoom("host 단독 삭제 테스트방");

        mockMvc.perform(delete("/api/study-room/{roomId}", roomIdAlone).with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("스터디룸 삭제가 완료되었습니다."));
    }

    // ========== 탈퇴 ==========

    @Test
    void memberLeaveRemovesMemberFromRoom() throws Exception {
        User host = saveUser("방장", "mgmt-leave-host");
        User member = saveUser("멤버", "mgmt-leave-member");
        authenticate(host.getId());
        Long roomId = createRoom("탈퇴 테스트방");
        String code = issueInviteCode(roomId);
        authenticate(member.getId());
        join(code);

        // member 탈퇴
        mockMvc.perform(delete("/api/study-room/{roomId}/leave", roomId).with(auth()))
                .andExpect(status().isOk());

        // 탈퇴 후 접근 → 멤버가 아님 (10002)
        mockMvc.perform(get("/api/study-room/{roomId}", roomId).with(auth()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(10002));
    }

    @Test
    void hostLeaveTransfersHostToNextMember() throws Exception {
        User host = saveUser("방장", "mgmt-transfer-host");
        User member = saveUser("멤버", "mgmt-transfer-member");
        authenticate(host.getId());
        Long roomId = createRoom("위임 테스트방");
        String code = issueInviteCode(roomId);
        authenticate(member.getId());
        join(code);

        // host 탈퇴 → member에게 방장 위임
        authenticate(host.getId());
        mockMvc.perform(delete("/api/study-room/{roomId}/leave", roomId).with(auth()))
                .andExpect(status().isOk());

        // member 기준으로 방 조회 → member가 방장, memberCount=1
        authenticate(member.getId());
        mockMvc.perform(get("/api/study-room/{roomId}", roomId).with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hostUserId").value(member.getId()))
                .andExpect(jsonPath("$.data.memberCount").value(1));
    }

    @Test
    void hostLeaveAloneDeletesRoom() throws Exception {
        User host = saveUser("방장", "mgmt-alone-host");
        authenticate(host.getId());
        Long roomId = createRoom("혼자 탈퇴 테스트방");

        // host 혼자 탈퇴 → 방 삭제
        mockMvc.perform(delete("/api/study-room/{roomId}/leave", roomId).with(auth()))
                .andExpect(status().isOk());

        // 방 조회 → 404 (10001)
        mockMvc.perform(get("/api/study-room/{roomId}", roomId).with(auth()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value(10001));
    }

    // ========== 주간 목표 ==========

    @Test
    void memberCanSetAndClearWeeklyGoalAndNegativeGoalIsRejected() throws Exception {
        User host = saveUser("방장", "mgmt-goal-host");
        authenticate(host.getId());
        Long roomId = createRoom("목표 테스트방");

        // weeklyGoal=20 설정 → 응답에 20 포함
        mockMvc.perform(put("/api/study-room/{roomId}/members/me/goal", roomId)
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new StudyRoomGoalUpdateRequest(20))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.weeklyGoal").value(20));

        // weeklyGoal=0 설정 → null로 초기화, HTTP 200
        mockMvc.perform(put("/api/study-room/{roomId}/members/me/goal", roomId)
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new StudyRoomGoalUpdateRequest(0))))
                .andExpect(status().isOk());

        // weeklyGoal=-1 → 유효성 오류 (10016)
        mockMvc.perform(put("/api/study-room/{roomId}/members/me/goal", roomId)
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new StudyRoomGoalUpdateRequest(-1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(10016));
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
        return readLong(result, "$.data.roomId");
    }

    private String issueInviteCode(Long roomId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/study-room/{roomId}/invite", roomId).with(auth()))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.code");
    }

    private void join(String code) throws Exception {
        mockMvc.perform(post("/api/study-room/join")
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new StudyRoomJoinRequest(code))))
                .andExpect(status().isOk());
    }

    private Long readLong(MvcResult result, String path) throws Exception {
        Number value = JsonPath.read(result.getResponse().getContentAsString(), path);
        return value.longValue();
    }
}
