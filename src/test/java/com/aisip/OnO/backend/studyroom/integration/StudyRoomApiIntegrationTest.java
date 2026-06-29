package com.aisip.OnO.backend.studyroom.integration;

import com.aisip.OnO.backend.problem.dto.ProblemRegisterDto;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.problem.repository.ProblemRepository;
import com.aisip.OnO.backend.problemsolve.entity.AnswerStatus;
import com.aisip.OnO.backend.problemsolve.entity.ProblemSolve;
import com.aisip.OnO.backend.problemsolve.repository.ProblemSolveRepository;
import com.aisip.OnO.backend.studyroom.dto.StudyRoomDtos.*;
import com.aisip.OnO.backend.studyroom.entity.StudyRoom;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomFeed;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomFeedEventType;
import com.aisip.OnO.backend.studyroom.repository.StudyRoomFeedRepository;
import com.aisip.OnO.backend.studyroom.repository.StudyRoomRepository;
import com.aisip.OnO.backend.user.entity.User;
import com.aisip.OnO.backend.user.repository.UserRepository;
import com.aisip.OnO.backend.util.RandomUserGenerator;
import com.aisip.OnO.backend.util.fileupload.service.FileUploadService;
import com.aisip.OnO.backend.config.rabbitmq.producer.S3DeleteProducer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StudyRoomApiIntegrationTest {

    private static final String EMOJI = "fired_up_sparkle_eyes";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudyRoomRepository roomRepository;

    @Autowired
    private StudyRoomFeedRepository feedRepository;

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private ProblemSolveRepository problemSolveRepository;

    @MockBean
    private FileUploadService fileUploadService;

    @MockBean
    private S3DeleteProducer s3DeleteProducer;

    private Long authenticatedUserId;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void roomCreateInviteJoinReadAndTodayPracticeSummaryApiFlow() throws Exception {
        User host = saveUser("방장", "room-flow-host");
        User member = saveUser("멤버", "room-flow-member");
        authenticate(host.getId());

        Long roomId = createRoom("수능 준비방");
        createPractice(host.getId(), LocalDateTime.now(), 2);

        String inviteCode = issueInviteCode(roomId);
        authenticate(member.getId());
        mockMvc.perform(post("/api/study-room/join")
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new StudyRoomJoinRequest(inviteCode))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roomId").value(roomId))
                .andExpect(jsonPath("$.data.memberCount").value(2));
        createPractice(member.getId(), LocalDateTime.now(), 3);

        mockMvc.perform(get("/api/study-room/{roomId}", roomId)
                        .with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memberCount").value(2))
                .andExpect(jsonPath("$.data.members[?(@.userId == " + host.getId() + ")].todayPracticeCount").value(2))
                .andExpect(jsonPath("$.data.members[?(@.userId == " + host.getId() + ")].practicedToday").value(true))
                .andExpect(jsonPath("$.data.members[?(@.userId == " + member.getId() + ")].todayPracticeCount").value(3))
                .andExpect(jsonPath("$.data.members[?(@.userId == " + member.getId() + ")].practicedToday").value(true));

        mockMvc.perform(get("/api/study-room")
                        .with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.roomId == " + roomId + ")].todayPracticeMemberCount").value(2))
                .andExpect(jsonPath("$.data[?(@.roomId == " + roomId + ")].todayPracticeCount").value(5));
    }

    @Test
    void hostCanUpdateRoomNameAndMemberCannotUpdateRoomName() throws Exception {
        User host = saveUser("방장", "room-update-host");
        User member = saveUser("멤버", "room-update-member");
        authenticate(host.getId());
        Long roomId = createRoom("기존 이름");
        String inviteCode = issueInviteCode(roomId);
        authenticate(member.getId());
        join(inviteCode);

        mockMvc.perform(patch("/api/study-room/{roomId}", roomId)
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new StudyRoomUpdateRequest("멤버 수정"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(10003));

        authenticate(host.getId());
        mockMvc.perform(patch("/api/study-room/{roomId}", roomId)
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new StudyRoomUpdateRequest("  새 스터디룸  "))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roomId").value(roomId))
                .andExpect(jsonPath("$.data.name").value("새 스터디룸"))
                .andExpect(jsonPath("$.data.memberCount").value(2));

        mockMvc.perform(patch("/api/study-room/{roomId}", roomId)
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new StudyRoomUpdateRequest(" "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(10016));
    }

    @Test
    void hostCanUpdateThumbnailAndResponsesIncludeThumbnailUrl() throws Exception {
        User host = saveUser("방장", "thumbnail-host");
        authenticate(host.getId());
        Long roomId = createRoom("썸네일방");
        MockMultipartFile thumbnail = new MockMultipartFile("thumbnail", "thumbnail.png", "image/png", pngBytes());
        given(fileUploadService.uploadFileToS3(any())).willReturn("https://cdn.example.com/study-room.png");

        mockMvc.perform(multipart("/api/study-room/{roomId}/thumbnail", roomId)
                        .file(thumbnail)
                        .with(request -> {
                            request.setMethod("PATCH");
                            return request;
                        })
                        .with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.thumbnailUrl").value("https://cdn.example.com/study-room.png"));

        mockMvc.perform(get("/api/study-room/{roomId}", roomId)
                        .with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.thumbnailUrl").value("https://cdn.example.com/study-room.png"));

        mockMvc.perform(get("/api/study-room")
                        .with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.roomId == " + roomId + ")].thumbnailUrl").value("https://cdn.example.com/study-room.png"));
    }

    @Test
    void hostCanUpdateRoomNameAndThumbnailInSingleMultipartRequest() throws Exception {
        User host = saveUser("방장", "room-multipart-host");
        authenticate(host.getId());
        Long roomId = createRoom("기존 통합 수정방");
        MockMultipartFile thumbnailImage = new MockMultipartFile("thumbnailImage", "thumbnail.png", "image/png", pngBytes());
        given(fileUploadService.uploadFileToS3(any())).willReturn("https://cdn.example.com/updated-room.png");

        mockMvc.perform(multipart("/api/study-rooms/{roomId}", roomId)
                        .file(thumbnailImage)
                        .param("name", "통합 수정방")
                        .with(request -> {
                            request.setMethod("PATCH");
                            return request;
                        })
                        .with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roomId").value(roomId))
                .andExpect(jsonPath("$.data.name").value("통합 수정방"))
                .andExpect(jsonPath("$.data.thumbnailUrl").value("https://cdn.example.com/updated-room.png"));
    }

    @Test
    void thumbnailUploadRejectsInvalidImageFile() throws Exception {
        User host = saveUser("방장", "thumbnail-invalid-host");
        authenticate(host.getId());
        Long roomId = createRoom("썸네일 검증방");
        MockMultipartFile thumbnail = new MockMultipartFile("thumbnail", "thumbnail.png", "image/png", "not-image".getBytes());

        mockMvc.perform(multipart("/api/study-room/{roomId}/thumbnail", roomId)
                        .file(thumbnail)
                        .with(request -> {
                            request.setMethod("PATCH");
                            return request;
                        })
                        .with(auth()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(2003));
    }

    @Test
    void nonMemberCannotReadRoomAndMemberCannotKickOtherMember() throws Exception {
        User host = saveUser("방장", "permission-host");
        User member = saveUser("멤버", "permission-member");
        User outsider = saveUser("외부", "permission-outsider");
        authenticate(host.getId());
        Long roomId = createRoom("권한 검증방");
        String inviteCode = issueInviteCode(roomId);
        authenticate(member.getId());
        join(inviteCode);

        authenticate(outsider.getId());
        mockMvc.perform(get("/api/study-room/{roomId}", roomId)
                        .with(auth()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(10002));

        authenticate(member.getId());
        mockMvc.perform(delete("/api/study-room/{roomId}/members/{memberId}", roomId, host.getId())
                        .with(auth()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(10003));
    }

    @Test
    void feedReactionToggleAndInvalidEmojiAreValidatedByApi() throws Exception {
        User host = saveUser("방장", "feed-host");
        authenticate(host.getId());
        Long roomId = createRoom("피드 반응방");
        StudyRoom room = roomRepository.findById(roomId).orElseThrow();
        StudyRoomFeed feed = feedRepository.save(StudyRoomFeed.create(room, host, StudyRoomFeedEventType.PROBLEM_REGISTERED, "{}"));

        mockMvc.perform(post("/api/study-room/{roomId}/feed/{feedId}/reactions", roomId, feed.getId())
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReactionToggleRequest(EMOJI))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.feedId").value(feed.getId()))
                .andExpect(jsonPath("$.data.reactions[0].emoji").value(EMOJI))
                .andExpect(jsonPath("$.data.reactions[0].count").value(1))
                .andExpect(jsonPath("$.data.reactions[0].reactedByMe").value(true));

        mockMvc.perform(post("/api/study-room/{roomId}/feed/{feedId}/reactions", roomId, feed.getId())
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReactionToggleRequest(EMOJI))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reactions").isEmpty());

        mockMvc.perform(post("/api/study-room/{roomId}/feed/{feedId}/reactions", roomId, feed.getId())
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReactionToggleRequest("not_allowed"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sharedProblemShareReactionAndDeleteAuthorizationApiFlow() throws Exception {
        User host = saveUser("방장", "shared-host");
        User member = saveUser("멤버", "shared-member");
        authenticate(host.getId());
        Long roomId = createRoom("공유 문제방");
        String inviteCode = issueInviteCode(roomId);
        Problem problem = createProblem(host.getId());
        authenticate(member.getId());
        join(inviteCode);

        authenticate(host.getId());
        Long sharedProblemId = shareProblem(roomId, problem.getId(), "같이 보면 좋은 문제");

        authenticate(member.getId());
        mockMvc.perform(post("/api/study-room/{roomId}/shared-problems/{sharedProblemId}/reactions", roomId, sharedProblemId)
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReactionToggleRequest(EMOJI))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sharedProblemId").value(sharedProblemId))
                .andExpect(jsonPath("$.data.reactions[0].count").value(1))
                .andExpect(jsonPath("$.data.reactions[0].reactedByMe").value(true));

        mockMvc.perform(delete("/api/study-room/{roomId}/shared-problems/{sharedProblemId}", roomId, sharedProblemId)
                        .with(auth()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(10002));

        authenticate(host.getId());
        mockMvc.perform(delete("/api/study-room/{roomId}/shared-problems/{sharedProblemId}", roomId, sharedProblemId)
                        .with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("공유 문제 삭제가 완료되었습니다."));
    }

    @Test
    void challengeApiUsesCurrentPeriodPracticeCountsAndKeepsPeriodChallengeInProgress() throws Exception {
        User host = saveUser("방장", "challenge-host");
        authenticate(host.getId());
        Long roomId = createRoom("챌린지방");
        LocalDateTime startAt = LocalDateTime.now().minusDays(10).truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime endAt = startAt.plusWeeks(3);

        mockMvc.perform(post("/api/study-room/{roomId}/challenges", roomId)
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChallengeCreateRequest(
                                "이번 주 복습 2회",
                                "individual",
                                "practice_count",
                                "weekly",
                                null,
                                2,
                                startAt,
                                endAt
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.period").value("weekly"));

        createPractice(host.getId(), startAt.plusDays(1), 4);
        createPractice(host.getId(), startAt.plusWeeks(1).plusHours(1), 2);

        mockMvc.perform(get("/api/study-room/{roomId}/challenges", roomId)
                        .with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("in_progress"))
                .andExpect(jsonPath("$.data[0].memberProgress[0].current").value(2))
                .andExpect(jsonPath("$.data[0].memberProgress[0].cleared").value(true));

        mockMvc.perform(post("/api/study-room/{roomId}/challenges", roomId)
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChallengeCreateRequest(
                                " ",
                                "individual",
                                "practice_count",
                                "weekly",
                                null,
                                2,
                                startAt,
                                endAt
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(10016));
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
                authenticatedUserId,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))
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

    private Long shareProblem(Long roomId, Long problemId, String comment) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/study-room/{roomId}/shared-problems", roomId)
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SharedProblemCreateRequest(problemId, comment))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.problemId").value(problemId))
                .andReturn();
        return readLong(result, "$.data.sharedProblemId");
    }

    private Long readLong(MvcResult result, String path) throws Exception {
        Number value = JsonPath.read(result.getResponse().getContentAsString(), path);
        return value.longValue();
    }

    private Problem createProblem(Long userId) {
        return problemRepository.save(Problem.from(
                new ProblemRegisterDto(null, "memo", "reference", null, LocalDateTime.now()),
                userId
        ));
    }

    private void createPractice(Long userId, LocalDateTime practicedAt, int count) {
        Problem problem = createProblem(userId);
        for (int i = 0; i < count; i++) {
            problemSolveRepository.save(ProblemSolve.create(
                    problem,
                    userId,
                    practicedAt.plusSeconds(i),
                    AnswerStatus.CORRECT,
                    null,
                    null,
                    60
            ));
        }
    }

    private byte[] pngBytes() {
        return new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47,
                0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D
        };
    }
}
