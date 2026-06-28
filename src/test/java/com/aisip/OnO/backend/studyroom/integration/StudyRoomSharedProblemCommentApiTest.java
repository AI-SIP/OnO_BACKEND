package com.aisip.OnO.backend.studyroom.integration;

import com.aisip.OnO.backend.problem.dto.ProblemRegisterDto;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.problem.repository.ProblemRepository;
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
class StudyRoomSharedProblemCommentApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProblemRepository problemRepository;

    private Long authenticatedUserId;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void memberCanCreateUpdateAndListSharedProblemComments() throws Exception {
        User host = saveUser("방장", "comment-flow-host");
        User member = saveUser("멤버", "comment-flow-member");
        authenticate(host.getId());
        Long roomId = createRoom("댓글 테스트방");
        String inviteCode = issueInviteCode(roomId);
        Long sharedProblemId = shareProblem(roomId, createProblem(host.getId()).getId(), "공유 문제");

        authenticate(member.getId());
        join(inviteCode);
        Long commentId = createComment(roomId, sharedProblemId, "처음 풀이 의견");

        mockMvc.perform(patch("/api/study-rooms/{roomId}/shared-problems/{sharedProblemId}/comments/{commentId}",
                        roomId, sharedProblemId, commentId)
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SharedProblemCommentRequest("수정된 풀이 의견"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.commentId").value(commentId))
                .andExpect(jsonPath("$.data.content").value("수정된 풀이 의견"))
                .andExpect(jsonPath("$.data.authorId").value(member.getId()))
                .andExpect(jsonPath("$.data.authorName").value("멤버"))
                .andExpect(jsonPath("$.data.isMine").value(true));

        MvcResult result = mockMvc.perform(get("/api/study-rooms/{roomId}/shared-problems/{sharedProblemId}/comments",
                        roomId, sharedProblemId)
                        .param("size", "10")
                        .with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].commentId").value(commentId))
                .andExpect(jsonPath("$.data.content[0].content").value("수정된 풀이 의견"))
                .andExpect(jsonPath("$.data.content[0].isMine").value(true))
                .andReturn();

        List<Object> comments = JsonPath.read(result.getResponse().getContentAsString(), "$.data.content");
        assertThat(comments).hasSize(1);
    }

    @Test
    void onlyAuthorCanUpdateAndHostCanDeleteOtherMemberComment() throws Exception {
        User host = saveUser("방장", "comment-auth-host");
        User member = saveUser("멤버", "comment-auth-member");
        User anotherMember = saveUser("다른멤버", "comment-auth-another");
        authenticate(host.getId());
        Long roomId = createRoom("댓글 권한방");
        String inviteCode = issueInviteCode(roomId);
        Long sharedProblemId = shareProblem(roomId, createProblem(host.getId()).getId(), "공유 문제");

        authenticate(member.getId());
        join(inviteCode);
        Long commentId = createComment(roomId, sharedProblemId, "멤버 댓글");

        authenticate(anotherMember.getId());
        join(inviteCode);
        mockMvc.perform(patch("/api/study-rooms/{roomId}/shared-problems/{sharedProblemId}/comments/{commentId}",
                        roomId, sharedProblemId, commentId)
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SharedProblemCommentRequest("권한 없는 수정"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(10019));

        authenticate(host.getId());
        mockMvc.perform(delete("/api/study-rooms/{roomId}/shared-problems/{sharedProblemId}/comments/{commentId}",
                        roomId, sharedProblemId, commentId)
                        .with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("공유 문제 댓글 삭제가 완료되었습니다."));

        mockMvc.perform(get("/api/study-rooms/{roomId}/shared-problems/{sharedProblemId}/comments", roomId, sharedProblemId)
                        .with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isEmpty());
    }

    @Test
    void commentPaginationUsesCursor() throws Exception {
        User host = saveUser("방장", "comment-page-host");
        authenticate(host.getId());
        Long roomId = createRoom("댓글 페이지방");
        Long sharedProblemId = shareProblem(roomId, createProblem(host.getId()).getId(), "공유 문제");
        for (int i = 0; i < 5; i++) {
            createComment(roomId, sharedProblemId, "댓글 " + i);
        }

        MvcResult firstResult = mockMvc.perform(get("/api/study-rooms/{roomId}/shared-problems/{sharedProblemId}/comments",
                        roomId, sharedProblemId)
                        .param("size", "3")
                        .with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andReturn();
        Number nextCursor = JsonPath.read(firstResult.getResponse().getContentAsString(), "$.data.nextCursor");

        MvcResult secondResult = mockMvc.perform(get("/api/study-rooms/{roomId}/shared-problems/{sharedProblemId}/comments",
                        roomId, sharedProblemId)
                        .param("size", "3")
                        .param("cursor", nextCursor.toString())
                        .with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andReturn();

        List<Object> comments = JsonPath.read(secondResult.getResponse().getContentAsString(), "$.data.content");
        assertThat(comments).hasSize(2);
    }

    @Test
    void blankOrTooLongCommentIsRejected() throws Exception {
        User host = saveUser("방장", "comment-invalid-host");
        authenticate(host.getId());
        Long roomId = createRoom("댓글 검증방");
        Long sharedProblemId = shareProblem(roomId, createProblem(host.getId()).getId(), "공유 문제");

        mockMvc.perform(post("/api/study-rooms/{roomId}/shared-problems/{sharedProblemId}/comments", roomId, sharedProblemId)
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SharedProblemCommentRequest(" "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(10018));

        mockMvc.perform(post("/api/study-rooms/{roomId}/shared-problems/{sharedProblemId}/comments", roomId, sharedProblemId)
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SharedProblemCommentRequest("a".repeat(301)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(10018));
    }

    private User saveUser(String name, String emailPrefix) {
        return userRepository.save(RandomUserGenerator.createRandomUser("GOOGLE", name, emailPrefix));
    }

    private void authenticate(Long userId) {
        authenticatedUserId = userId;
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        userId,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))
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

    private Problem createProblem(Long userId) {
        return problemRepository.save(Problem.from(
                new ProblemRegisterDto(null, "memo", "reference", null, LocalDateTime.now()),
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
        return readLong(result, "$.data.sharedProblemId");
    }

    private Long createComment(Long roomId, Long sharedProblemId, String content) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/study-rooms/{roomId}/shared-problems/{sharedProblemId}/comments",
                        roomId, sharedProblemId)
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SharedProblemCommentRequest(content))))
                .andExpect(status().isCreated())
                .andReturn();
        return readLong(result, "$.data.commentId");
    }

    private Long readLong(MvcResult result, String path) throws Exception {
        Number value = JsonPath.read(result.getResponse().getContentAsString(), path);
        return value.longValue();
    }
}
