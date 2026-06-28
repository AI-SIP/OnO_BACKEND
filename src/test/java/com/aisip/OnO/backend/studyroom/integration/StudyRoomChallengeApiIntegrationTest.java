package com.aisip.OnO.backend.studyroom.integration;

import com.aisip.OnO.backend.problem.dto.ProblemRegisterDto;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.problem.repository.ProblemRepository;
import com.aisip.OnO.backend.problemsolve.entity.AnswerStatus;
import com.aisip.OnO.backend.problemsolve.entity.ProblemSolve;
import com.aisip.OnO.backend.problemsolve.repository.ProblemSolveRepository;
import com.aisip.OnO.backend.studyroom.dto.StudyRoomDtos.ChallengeCreateRequest;
import com.aisip.OnO.backend.studyroom.dto.StudyRoomDtos.InviteCodeResponse;
import com.aisip.OnO.backend.studyroom.dto.StudyRoomDtos.StudyRoomCreateRequest;
import com.aisip.OnO.backend.studyroom.dto.StudyRoomDtos.StudyRoomDetailResponse;
import com.aisip.OnO.backend.studyroom.dto.StudyRoomDtos.StudyRoomJoinRequest;
import com.aisip.OnO.backend.studyroom.service.StudyRoomInviteService;
import com.aisip.OnO.backend.studyroom.service.StudyRoomService;
import com.aisip.OnO.backend.user.entity.User;
import com.aisip.OnO.backend.user.repository.UserRepository;
import com.aisip.OnO.backend.util.RandomUserGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StudyRoomChallengeApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StudyRoomService studyRoomService;

    @Autowired
    private StudyRoomInviteService inviteService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private ProblemSolveRepository problemSolveRepository;

    private User host;
    private User member;
    private User outsider;
    private Long roomId;

    @BeforeEach
    void setUp() {
        host = userRepository.save(RandomUserGenerator.createRandomUser("GOOGLE", "방장", "challenge-host"));
        member = userRepository.save(RandomUserGenerator.createRandomUser("GOOGLE", "멤버", "challenge-member"));
        outsider = userRepository.save(RandomUserGenerator.createRandomUser("GOOGLE", "외부", "challenge-outsider"));

        authenticate(host.getId());
        StudyRoomDetailResponse room = studyRoomService.createRoom(new StudyRoomCreateRequest("챌린지 테스트방"), host.getId());
        InviteCodeResponse invite = inviteService.issueInviteCode(room.roomId(), host.getId());
        inviteService.join(new StudyRoomJoinRequest(invite.code()), member.getId());
        roomId = room.roomId();
    }

    @Test
    void memberCanCreateWeeklyChallengeAndProgressCountsOnlyCurrentPeriod() throws Exception {
        LocalDateTime startAt = LocalDateTime.now().minusDays(10).withNano(0);
        LocalDateTime currentPeriodStart = startAt.plusWeeks(1);
        createPractice(member.getId(), startAt.plusDays(2));
        createPractice(member.getId(), currentPeriodStart.plusDays(1));
        createPractice(member.getId(), currentPeriodStart.plusDays(2));
        ChallengeCreateRequest request = new ChallengeCreateRequest(
                "이번 주 복습 2개",
                "individual",
                "practice_count",
                "weekly",
                2,
                startAt,
                startAt.plusWeeks(3)
        );

        authenticate(member.getId());
        MvcResult result = mockMvc.perform(post("/api/study-room/{roomId}/challenges", roomId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.title").value("이번 주 복습 2개"))
                .andExpect(jsonPath("$.data.type").value("individual"))
                .andExpect(jsonPath("$.data.metric").value("practice_count"))
                .andExpect(jsonPath("$.data.period").value("weekly"))
                .andExpect(jsonPath("$.data.status").value("in_progress"))
                .andReturn();

        List<Map<String, Object>> memberProgress = JsonPath.read(
                result.getResponse().getContentAsString(),
                "$.data.memberProgress"
        );
        Map<String, Object> targetMemberProgress = memberProgress.stream()
                .filter(progress -> ((Number) progress.get("userId")).longValue() == member.getId())
                .findFirst()
                .orElseThrow();
        assertThat(targetMemberProgress.get("current")).isEqualTo(2);
        assertThat(targetMemberProgress.get("cleared")).isEqualTo(true);
    }

    @Test
    void nonMemberCannotGetChallenges() throws Exception {
        authenticate(outsider.getId());

        mockMvc.perform(get("/api/study-room/{roomId}/challenges", roomId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(10002));
    }

    @Test
    void createChallengeRejectsInvalidPeriod() throws Exception {
        ChallengeCreateRequest request = new ChallengeCreateRequest(
                "잘못된 기간",
                "individual",
                "practice_count",
                "yearly",
                1,
                null,
                LocalDateTime.now().plusDays(1)
        );

        authenticate(member.getId());
        mockMvc.perform(post("/api/study-room/{roomId}/challenges", roomId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(10016));
    }

    @Test
    void hostCanDeleteChallengeAndMemberCannot() throws Exception {
        ChallengeCreateRequest request = new ChallengeCreateRequest(
                "삭제 테스트", "individual", "practice_count", null,
                3, null, LocalDateTime.now().plusDays(7)
        );
        // 방장으로 챌린지 생성: 세션 간 인증 공유 문제를 피하기 위해 auth() RequestPostProcessor 사용
        MvcResult createResult = mockMvc.perform(post("/api/study-room/{roomId}/challenges", roomId)
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                host.getId(), null, List.of(new SimpleGrantedAuthority("ROLE_MEMBER")))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        Number challengeIdValue = JsonPath.read(createResult.getResponse().getContentAsString(), "$.data.challengeId");
        Long challengeId = challengeIdValue.longValue();

        // 멤버는 삭제 불가 - HOST_ONLY (10003)
        mockMvc.perform(delete("/api/study-room/{roomId}/challenges/{challengeId}", roomId, challengeId)
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                member.getId(), null, List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(10003));

        // 방장은 삭제 가능
        mockMvc.perform(delete("/api/study-room/{roomId}/challenges/{challengeId}", roomId, challengeId)
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                host.getId(), null, List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("챌린지 삭제가 완료되었습니다."));
    }

    @Test
    void createChallengeRejectsTitleTooLong() throws Exception {
        ChallengeCreateRequest request = new ChallengeCreateRequest(
                "a".repeat(41), "individual", "practice_count", null,
                3, null, LocalDateTime.now().plusDays(7)
        );
        authenticate(member.getId());
        mockMvc.perform(post("/api/study-room/{roomId}/challenges", roomId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(10016));
    }

    @Test
    void createChallengeRejectsPastEndAt() throws Exception {
        ChallengeCreateRequest request = new ChallengeCreateRequest(
                "과거 종료일", "individual", "practice_count", null,
                3, null, LocalDateTime.now().minusDays(1)
        );
        authenticate(member.getId());
        mockMvc.perform(post("/api/study-room/{roomId}/challenges", roomId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(10016));
    }

    @Test
    void createChallengeRejectsStartAtAfterEndAt() throws Exception {
        ChallengeCreateRequest request = new ChallengeCreateRequest(
                "날짜 역전", "individual", "practice_count", null,
                3, LocalDateTime.now().plusDays(5), LocalDateTime.now().plusDays(3)
        );
        authenticate(member.getId());
        mockMvc.perform(post("/api/study-room/{roomId}/challenges", roomId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(10016));
    }

    private void authenticate(Long userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        userId,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))
                )
        );
    }

    private void createPractice(Long userId, LocalDateTime practicedAt) {
        Problem problem = problemRepository.save(Problem.from(
                new ProblemRegisterDto(null, "memo", "reference", null, LocalDateTime.now()),
                userId
        ));
        problemSolveRepository.save(ProblemSolve.create(
                problem,
                userId,
                practicedAt,
                AnswerStatus.CORRECT,
                null,
                null,
                60
        ));
    }
}
