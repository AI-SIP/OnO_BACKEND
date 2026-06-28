package com.aisip.OnO.backend.studyroom.integration;

import com.aisip.OnO.backend.problem.dto.ProblemRegisterDto;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.problem.repository.ProblemRepository;
import com.aisip.OnO.backend.problemsolve.repository.ProblemSolveRepository;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StudyRoomSharedProblemApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private ProblemSolveRepository problemSolveRepository;

    private Long authenticatedUserId;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void nonMemberCannotGetSharedProblems() throws Exception {
        User host = saveUser("방장", "shared-nonmember-host");
        User outsider = saveUser("외부인", "shared-nonmember-outsider");
        authenticate(host.getId());
        Long roomId = createRoom("공유 문제 접근 테스트방");

        // 외부인이 공유 문제 목록 조회 → 403 (10002)
        authenticate(outsider.getId());
        mockMvc.perform(get("/api/study-room/{roomId}/shared-problems", roomId).with(auth()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(10002));
    }

    @Test
    void commentTooLongIsRejected() throws Exception {
        User host = saveUser("방장", "shared-comment-host");
        authenticate(host.getId());
        Long roomId = createRoom("댓글 길이 테스트방");
        Problem problem = createProblem(host.getId());

        // 101자 댓글 → 유효성 오류 (10016)
        String tooLongComment = "a".repeat(101);
        mockMvc.perform(post("/api/study-room/{roomId}/shared-problems", roomId)
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SharedProblemCreateRequest(problem.getId(), tooLongComment))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(10016));
    }

    @Test
    void nonSharerMemberCannotDeleteSharedProblem() throws Exception {
        User host = saveUser("방장", "shared-delete-perm-host");
        User member = saveUser("멤버", "shared-delete-perm-member");
        authenticate(host.getId());
        Long roomId = createRoom("삭제 권한 테스트방");
        String code = issueInviteCode(roomId);
        Problem problem = createProblem(host.getId());
        authenticate(member.getId());
        join(code);

        // host가 문제 공유
        authenticate(host.getId());
        Long sharedProblemId = shareProblem(roomId, problem.getId(), "공유 댓글");

        // member가 host 공유 문제 삭제 시도 → 403 (10002)
        authenticate(member.getId());
        mockMvc.perform(delete("/api/study-room/{roomId}/shared-problems/{sharedProblemId}", roomId, sharedProblemId)
                        .with(auth()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(10002));
    }

    @Test
    void sharerCanDeleteOwnSharedProblem() throws Exception {
        User host = saveUser("방장", "shared-delete-own-host");
        authenticate(host.getId());
        Long roomId = createRoom("본인 삭제 테스트방");
        Problem problem = createProblem(host.getId());

        // host가 공유 후 본인 삭제
        Long sharedProblemId = shareProblem(roomId, problem.getId(), "내 공유 문제");

        mockMvc.perform(delete("/api/study-room/{roomId}/shared-problems/{sharedProblemId}", roomId, sharedProblemId)
                        .with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("공유 문제 삭제가 완료되었습니다."));

        // 삭제 후 목록 조회 → 빈 리스트
        MvcResult listResult = mockMvc.perform(get("/api/study-room/{roomId}/shared-problems", roomId).with(auth()))
                .andExpect(status().isOk())
                .andReturn();
        List<Object> content = JsonPath.read(listResult.getResponse().getContentAsString(), "$.data.content");
        assertThat(content).isEmpty();
    }

    @Test
    void sharedProblemPaginationWithCursor() throws Exception {
        User host = saveUser("방장", "shared-pagination-host");
        authenticate(host.getId());
        Long roomId = createRoom("공유 문제 페이지네이션 테스트방");

        // 5개 문제 공유
        for (int i = 0; i < 5; i++) {
            Problem problem = createProblem(host.getId());
            shareProblem(roomId, problem.getId(), "댓글 " + i);
        }

        // 첫 번째 페이지: size=3 → 3개, hasNext=true
        MvcResult firstResult = mockMvc.perform(get("/api/study-room/{roomId}/shared-problems", roomId)
                        .param("size", "3")
                        .with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andReturn();

        String firstBody = firstResult.getResponse().getContentAsString();
        List<Object> firstContent = JsonPath.read(firstBody, "$.data.content");
        assertThat(firstContent).hasSize(3);
        Number nextCursor = JsonPath.read(firstBody, "$.data.nextCursor");

        // 두 번째 페이지: cursor=nextCursor → 2개, hasNext=false
        MvcResult secondResult = mockMvc.perform(get("/api/study-room/{roomId}/shared-problems", roomId)
                        .param("size", "3")
                        .param("cursor", nextCursor.toString())
                        .with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andReturn();

        String secondBody = secondResult.getResponse().getContentAsString();
        List<Object> secondContent = JsonPath.read(secondBody, "$.data.content");
        assertThat(secondContent).hasSize(2);
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

    private void join(String code) throws Exception {
        mockMvc.perform(post("/api/study-room/join")
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new StudyRoomJoinRequest(code))))
                .andExpect(status().isOk());
    }

    private Problem createProblem(Long userId) {
        return problemRepository.save(Problem.from(
                new ProblemRegisterDto(null, "memo", "ref", null, LocalDateTime.now()),
                userId
        ));
    }

    private Long shareProblem(Long roomId, Long problemId, String comment) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/study-room/{roomId}/shared-problems", roomId)
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SharedProblemCreateRequest(problemId, comment))))
                .andExpect(status().isCreated())
                .andReturn();
        Number value = JsonPath.read(result.getResponse().getContentAsString(), "$.data.sharedProblemId");
        return value.longValue();
    }
}
