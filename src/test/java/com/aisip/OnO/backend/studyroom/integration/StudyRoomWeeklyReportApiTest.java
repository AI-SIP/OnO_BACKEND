package com.aisip.OnO.backend.studyroom.integration;

import com.aisip.OnO.backend.studyroom.dto.StudyRoomDtos.*;
import com.aisip.OnO.backend.studyroom.entity.StudyRoom;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomWeeklyReport;
import com.aisip.OnO.backend.studyroom.repository.StudyRoomRepository;
import com.aisip.OnO.backend.studyroom.repository.StudyRoomWeeklyReportRepository;
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

import java.time.LocalDate;
import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StudyRoomWeeklyReportApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudyRoomRepository roomRepository;

    @Autowired
    private StudyRoomWeeklyReportRepository reportRepository;

    private Long authenticatedUserId;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void nonMemberCannotGetWeeklyReports() throws Exception {
        User host = saveUser("방장", "report-nonmember-host");
        User outsider = saveUser("외부", "report-nonmember-outsider");
        authenticate(host.getId());
        Long roomId = createRoom("리포트 권한 테스트방");

        authenticate(outsider.getId());
        mockMvc.perform(get("/api/study-room/{roomId}/weekly-reports", roomId)
                        .with(auth()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(10002));
    }

    @Test
    void memberGetsEmptyListWhenNoReportsExist() throws Exception {
        User host = saveUser("방장", "report-empty-host");
        authenticate(host.getId());
        Long roomId = createRoom("빈 리포트방");

        mockMvc.perform(get("/api/study-room/{roomId}/weekly-reports", roomId)
                        .with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void memberCanGetReportAndIsReadStatusIsCorrect() throws Exception {
        User host = saveUser("방장", "report-read-host");
        User member = saveUser("멤버", "report-read-member");
        authenticate(host.getId());
        Long roomId = createRoom("리포트 읽음 테스트방");
        String inviteCode = issueInviteCode(roomId);
        authenticate(member.getId());
        join(inviteCode);

        StudyRoom room = roomRepository.findById(roomId).orElseThrow();
        StudyRoomWeeklyReport report = reportRepository.save(StudyRoomWeeklyReport.create(
                room,
                LocalDate.now().minusDays(7),
                LocalDate.now().minusDays(1),
                "방장", 5,
                "방장", 3,
                5, 0,
                "이번 주도 수고하셨습니다!"
        ));

        // 읽기 전: isRead=false
        authenticate(member.getId());
        mockMvc.perform(get("/api/study-room/{roomId}/weekly-reports", roomId)
                        .with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].isRead").value(false));

        // 읽음 처리: 응답에서 isRead=true
        mockMvc.perform(patch("/api/study-room/{roomId}/weekly-reports/{reportId}/read", roomId, report.getId())
                        .with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isRead").value(true));

        // 다시 조회: isRead=true
        mockMvc.perform(get("/api/study-room/{roomId}/weekly-reports", roomId)
                        .with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].isRead").value(true));
    }

    @Test
    void markReadOnNonExistentReportThrowsNotFound() throws Exception {
        User host = saveUser("방장", "report-notfound-host");
        User member = saveUser("멤버", "report-notfound-member");
        authenticate(host.getId());
        Long roomId = createRoom("리포트 없음 테스트방");
        String inviteCode = issueInviteCode(roomId);
        authenticate(member.getId());
        join(inviteCode);

        mockMvc.perform(patch("/api/study-room/{roomId}/weekly-reports/{reportId}/read", roomId, 9999999L)
                        .with(auth()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value(10014));
    }

    @Test
    void nonMemberCannotMarkReportRead() throws Exception {
        User host = saveUser("방장", "report-forbid-host");
        User outsider = saveUser("외부", "report-forbid-outsider");
        authenticate(host.getId());
        Long roomId = createRoom("리포트 금지 테스트방");

        StudyRoom room = roomRepository.findById(roomId).orElseThrow();
        StudyRoomWeeklyReport report = reportRepository.save(StudyRoomWeeklyReport.create(
                room,
                LocalDate.now().minusDays(7),
                LocalDate.now().minusDays(1),
                "방장", 5,
                "방장", 3,
                5, 0,
                "이번 주도 수고하셨습니다!"
        ));

        authenticate(outsider.getId());
        mockMvc.perform(patch("/api/study-room/{roomId}/weekly-reports/{reportId}/read", roomId, report.getId())
                        .with(auth()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(10002));
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
