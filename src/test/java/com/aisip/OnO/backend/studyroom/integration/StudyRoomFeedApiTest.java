package com.aisip.OnO.backend.studyroom.integration;

import com.aisip.OnO.backend.studyroom.dto.StudyRoomDtos.*;
import com.aisip.OnO.backend.studyroom.entity.StudyRoom;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomFeed;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomFeedEventType;
import com.aisip.OnO.backend.studyroom.repository.StudyRoomFeedRepository;
import com.aisip.OnO.backend.studyroom.repository.StudyRoomRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StudyRoomFeedApiTest {

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

    private Long authenticatedUserId;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void nonMemberCannotGetFeed() throws Exception {
        User host = saveUser("방장", "feed-nonmember-host");
        User outsider = saveUser("외부인", "feed-nonmember-outsider");
        authenticate(host.getId());
        Long roomId = createRoom("피드 접근 테스트방");

        // 멤버가 아닌 외부인이 피드 조회 → 403 (10002)
        authenticate(outsider.getId());
        mockMvc.perform(get("/api/study-room/{roomId}/feed", roomId).with(auth()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(10002));
    }

    @Test
    void feedPaginationWithCursor() throws Exception {
        User host = saveUser("방장", "feed-pagination-host");
        authenticate(host.getId());
        Long roomId = createRoom("피드 페이지네이션 테스트방");

        // 피드 5개 직접 저장 (DB에 직접 저장 → 연속 ID 보장)
        StudyRoom room = roomRepository.findById(roomId).orElseThrow();
        for (int i = 0; i < 5; i++) {
            feedRepository.save(StudyRoomFeed.create(room, host, StudyRoomFeedEventType.SESSION_STARTED, "{}"));
        }

        // 첫 번째 페이지: size=3 → 3개, hasNext=true
        MvcResult firstResult = mockMvc.perform(get("/api/study-room/{roomId}/feed", roomId)
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
        MvcResult secondResult = mockMvc.perform(get("/api/study-room/{roomId}/feed", roomId)
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

    @Test
    void multipleUsersCanReactToSameFeed() throws Exception {
        User host = saveUser("방장", "feed-react-host");
        User member = saveUser("멤버", "feed-react-member");
        authenticate(host.getId());
        Long roomId = createRoom("피드 반응 테스트방");
        String code = issueInviteCode(roomId);
        authenticate(member.getId());
        join(code);

        // 피드 1개 직접 저장
        StudyRoom room = roomRepository.findById(roomId).orElseThrow();
        StudyRoomFeed feed = feedRepository.save(
                StudyRoomFeed.create(room, host, StudyRoomFeedEventType.SESSION_STARTED, "{}"));

        // host가 반응 추가 → count=1
        authenticate(host.getId());
        mockMvc.perform(post("/api/study-room/{roomId}/feed/{feedId}/reactions", roomId, feed.getId())
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReactionToggleRequest(EMOJI))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reactions[0].count").value(1));

        // member가 동일 이모지로 반응 추가 → count=2, reactedByMe=true (member 기준)
        authenticate(member.getId());
        mockMvc.perform(post("/api/study-room/{roomId}/feed/{feedId}/reactions", roomId, feed.getId())
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReactionToggleRequest(EMOJI))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reactions[0].count").value(2))
                .andExpect(jsonPath("$.data.reactions[0].reactedByMe").value(true));
    }

    @Test
    void nonMemberCannotToggleFeedReaction() throws Exception {
        User host = saveUser("방장", "feed-react-perm-host");
        User outsider = saveUser("외부인", "feed-react-perm-outsider");
        authenticate(host.getId());
        Long roomId = createRoom("반응 권한 테스트방");

        // 피드 1개 직접 저장
        StudyRoom room = roomRepository.findById(roomId).orElseThrow();
        StudyRoomFeed feed = feedRepository.save(
                StudyRoomFeed.create(room, host, StudyRoomFeedEventType.SESSION_STARTED, "{}"));

        // 외부인이 반응 시도 → 403 (10002)
        authenticate(outsider.getId());
        mockMvc.perform(post("/api/study-room/{roomId}/feed/{feedId}/reactions", roomId, feed.getId())
                        .with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReactionToggleRequest(EMOJI))))
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
}
