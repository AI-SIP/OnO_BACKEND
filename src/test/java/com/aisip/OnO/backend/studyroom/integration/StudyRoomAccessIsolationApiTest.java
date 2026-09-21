package com.aisip.OnO.backend.studyroom.integration;

import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.studyroom.dto.StudyRoomDtos.*;
import com.aisip.OnO.backend.studyroom.entity.*;
import com.aisip.OnO.backend.studyroom.support.StudyRoomTestSupport;
import com.aisip.OnO.backend.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 스터디룸 전 엔드포인트에 대한 접근 격리 검증.
 *
 * <p>이 프로젝트의 핵심 불변식은 "사용자는 자기 데이터에만 접근한다"이고, 스터디룸은
 * 그 경계가 가장 복잡한 곳이다. 엔드포인트가 하나 늘 때마다 방 소속 검증을 빠뜨릴 여지가
 * 생기므로, 개별 기능 테스트와 별개로 <b>모든 엔드포인트를 한 번에 훑는</b> 매트릭스를 둔다.
 *
 * <p>여기서 검증하는 것은 두 가지다.
 * <ul>
 *   <li>방에 속하지 않은 사용자는 어떤 엔드포인트도 통과하지 못한다 (403 / 10002)</li>
 *   <li>인증 없는 요청은 어떤 엔드포인트도 통과하지 못한다 (401)</li>
 * </ul>
 *
 * <p>멀티파트 엔드포인트(썸네일 업로드, 멀티파트 생성·수정)는 요청 빌더가 달라
 * {@link StudyRoomApiTest} 에서 개별로 검증한다.
 */
@DisplayName("스터디룸 접근 격리 — 비멤버·미인증 매트릭스")
class StudyRoomAccessIsolationApiTest extends StudyRoomTestSupport {

    /** 요청 이름과 빌더. 방 리소스는 모두 host 소유로 미리 만들어 둔다. */
    private record Endpoint(String name, Supplier<MockHttpServletRequestBuilder> request) {
    }

    private List<Endpoint> allEndpoints(RoomFixture fixture) throws Exception {
        Long roomId = fixture.roomId();
        Problem problem = saveProblem(fixture.host().getId());
        StudyRoomFeed feed = saveFeed(fixture.room(), fixture.host());
        StudyRoomChallenge challenge = saveChallenge(fixture.room(), 5);
        StudyRoomSharedProblem shared = saveSharedProblem(fixture.room(), fixture.host(), problem, "코멘트");
        StudyRoomSharedProblemComment comment = saveComment(shared, fixture.host(), "댓글");
        StudyRoomWeeklyReport report = saveWeeklyReport(fixture.room(), LocalDate.of(2026, 1, 5));
        Problem outsiderProblem = saveProblem(fixture.outsider().getId());

        String goalBody = objectMapper.writeValueAsString(new StudyRoomGoalUpdateRequest(5));
        String nameBody = objectMapper.writeValueAsString(new StudyRoomUpdateRequest("침입자 수정"));
        String emojiBody = objectMapper.writeValueAsString(new ReactionToggleRequest(EMOJI));
        String commentBody = objectMapper.writeValueAsString(new SharedProblemCommentRequest("침입자 댓글"));
        String shareBody = objectMapper.writeValueAsString(
                new SharedProblemCreateRequest(outsiderProblem.getId(), "침입자 공유"));
        String thumbnailBody = objectMapper.writeValueAsString(
                new ThumbnailUrlUpdateRequest("https://cdn.example.com/hijack.png"));
        String challengeBody = objectMapper.writeValueAsString(new ChallengeCreateRequest(
                "침입자 챌린지", "individual", "problem_count", null, null, 1, null,
                LocalDateTime.now().plusDays(3)));

        List<Endpoint> endpoints = new ArrayList<>();
        endpoints.add(new Endpoint("GET 방 상세",
                () -> get("/api/study-room/{roomId}", roomId)));
        endpoints.add(new Endpoint("PATCH 방 수정",
                () -> patch("/api/study-room/{roomId}", roomId).contentType(MediaType.APPLICATION_JSON).content(nameBody)));
        endpoints.add(new Endpoint("DELETE 방 삭제",
                () -> delete("/api/study-room/{roomId}", roomId)));
        endpoints.add(new Endpoint("PATCH 썸네일 URL 변경",
                () -> patch("/api/study-room/{roomId}/thumbnail-url", roomId).contentType(MediaType.APPLICATION_JSON).content(thumbnailBody)));
        endpoints.add(new Endpoint("POST 초대 코드 발급",
                () -> post("/api/study-room/{roomId}/invite", roomId)));
        endpoints.add(new Endpoint("DELETE 탈퇴",
                () -> delete("/api/study-room/{roomId}/leave", roomId)));
        endpoints.add(new Endpoint("DELETE 멤버 강퇴",
                () -> delete("/api/study-room/{roomId}/members/{memberId}", roomId, fixture.member().getId())));
        endpoints.add(new Endpoint("PUT 주간 목표 설정",
                () -> put("/api/study-room/{roomId}/members/me/goal", roomId).contentType(MediaType.APPLICATION_JSON).content(goalBody)));
        endpoints.add(new Endpoint("GET 챌린지 목록",
                () -> get("/api/study-room/{roomId}/challenges", roomId)));
        endpoints.add(new Endpoint("POST 챌린지 생성",
                () -> post("/api/study-room/{roomId}/challenges", roomId).contentType(MediaType.APPLICATION_JSON).content(challengeBody)));
        endpoints.add(new Endpoint("DELETE 챌린지 삭제",
                () -> delete("/api/study-room/{roomId}/challenges/{challengeId}", roomId, challenge.getId())));
        endpoints.add(new Endpoint("GET 피드",
                () -> get("/api/study-room/{roomId}/feed", roomId)));
        endpoints.add(new Endpoint("POST 피드 리액션",
                () -> post("/api/study-room/{roomId}/feed/{feedId}/reactions", roomId, feed.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(emojiBody)));
        endpoints.add(new Endpoint("GET 공유 문제 목록",
                () -> get("/api/study-room/{roomId}/shared-problems", roomId)));
        endpoints.add(new Endpoint("POST 문제 공유",
                () -> post("/api/study-room/{roomId}/shared-problems", roomId)
                        .contentType(MediaType.APPLICATION_JSON).content(shareBody)));
        endpoints.add(new Endpoint("POST 공유 문제 리액션",
                () -> post("/api/study-room/{roomId}/shared-problems/{id}/reactions", roomId, shared.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(emojiBody)));
        endpoints.add(new Endpoint("DELETE 공유 문제 삭제",
                () -> delete("/api/study-room/{roomId}/shared-problems/{id}", roomId, shared.getId())));
        endpoints.add(new Endpoint("GET 공유 문제 댓글 목록",
                () -> get("/api/study-room/{roomId}/shared-problems/{id}/comments", roomId, shared.getId())));
        endpoints.add(new Endpoint("POST 공유 문제 댓글 작성",
                () -> post("/api/study-room/{roomId}/shared-problems/{id}/comments", roomId, shared.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(commentBody)));
        endpoints.add(new Endpoint("PATCH 공유 문제 댓글 수정",
                () -> patch("/api/study-room/{roomId}/shared-problems/{id}/comments/{commentId}",
                        roomId, shared.getId(), comment.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(commentBody)));
        endpoints.add(new Endpoint("POST 공유 문제 댓글 리액션",
                () -> post("/api/study-room/{roomId}/shared-problems/{id}/comments/{commentId}/reactions",
                        roomId, shared.getId(), comment.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(emojiBody)));
        endpoints.add(new Endpoint("DELETE 공유 문제 댓글 삭제",
                () -> delete("/api/study-room/{roomId}/shared-problems/{id}/comments/{commentId}",
                        roomId, shared.getId(), comment.getId())));
        endpoints.add(new Endpoint("GET 주간 리포트 목록",
                () -> get("/api/study-room/{roomId}/weekly-reports", roomId)));
        endpoints.add(new Endpoint("PATCH 주간 리포트 읽음",
                () -> patch("/api/study-room/{roomId}/weekly-reports/{reportId}/read", roomId, report.getId())));
        return endpoints;
    }

    @TestFactory
    @DisplayName("방에 속하지 않은 사용자는 어떤 엔드포인트도 통과하지 못한다")
    List<DynamicTest> nonMemberIsRejectedEverywhere() throws Exception {
        RoomFixture fixture = createRoomWithMemberAndOutsider();
        List<Endpoint> endpoints = allEndpoints(fixture);

        return endpoints.stream()
                .map(endpoint -> DynamicTest.dynamicTest(endpoint.name(), () -> {
                    authenticateAs(fixture.outsider().getId());
                    mockMvc.perform(endpoint.request().get())
                            .andExpect(status().isForbidden())
                            .andExpect(jsonPath("$.errorCode").value(10002));
                }))
                .toList();
    }

    @TestFactory
    @DisplayName("인증 없는 요청은 어떤 엔드포인트도 통과하지 못한다")
    List<DynamicTest> unauthenticatedIsRejectedEverywhere() throws Exception {
        RoomFixture fixture = createRoomWithMemberAndOutsider();
        List<Endpoint> endpoints = allEndpoints(fixture);

        return endpoints.stream()
                .map(endpoint -> DynamicTest.dynamicTest(endpoint.name(), () -> {
                    clearAuthentication();
                    mockMvc.perform(endpoint.request().get())
                            .andExpect(status().isUnauthorized());
                }))
                .toList();
    }

    @Nested
    @DisplayName("거절된 요청은 데이터를 바꾸지 않는다")
    class RejectedRequestsLeaveNoTrace {

        @Test
        @DisplayName("비멤버의 쓰기 시도는 방·멤버·피드·공유문제·댓글 어느 것도 건드리지 않는다")
        void nonMemberWritesChangeNothing() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            List<Endpoint> endpoints = allEndpoints(fixture);
            long roomCount = roomRepository.count();
            long memberCount = memberRepository.countByRoomId(fixture.roomId());
            long feedCount = feedRepository.count();
            long sharedCount = sharedProblemRepository.count();
            long commentCount = commentRepository.count();
            long challengeCount = challengeRepository.count();
            String originalName = fixture.room().getName();

            authenticateAs(fixture.outsider().getId());
            for (Endpoint endpoint : endpoints) {
                mockMvc.perform(endpoint.request().get()).andExpect(status().isForbidden());
            }

            assertThat(roomRepository.count()).as("방 수").isEqualTo(roomCount);
            assertThat(memberRepository.countByRoomId(fixture.roomId())).as("멤버 수").isEqualTo(memberCount);
            assertThat(feedRepository.count()).as("피드 수").isEqualTo(feedCount);
            assertThat(sharedProblemRepository.count()).as("공유 문제 수").isEqualTo(sharedCount);
            assertThat(commentRepository.count()).as("댓글 수").isEqualTo(commentCount);
            assertThat(challengeRepository.count()).as("챌린지 수").isEqualTo(challengeCount);
            assertThat(feedReactionRepository.count()).as("피드 리액션 수").isZero();
            assertThat(sharedProblemReactionRepository.count()).as("공유 문제 리액션 수").isZero();
            assertThat(commentReactionRepository.count()).as("댓글 리액션 수").isZero();
            assertThat(weeklyReportReadRepository.count()).as("리포트 읽음 기록 수").isZero();
            assertThat(inviteCodeRepository.count()).as("초대 코드 수").isZero();
            assertThat(roomRepository.findById(fixture.roomId()).orElseThrow().getName())
                    .as("방 이름").isEqualTo(originalName);
        }
    }

    @Nested
    @DisplayName("방장 전용 엔드포인트")
    class HostOnlyEndpoints {

        @Test
        @DisplayName("일반 멤버는 방 수정·삭제·썸네일·챌린지 삭제·강퇴를 할 수 없다")
        void memberIsRejectedOnHostOnlyEndpoints() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            User third = fixtures.createUser("third");
            addMember(fixture.room(), third);
            StudyRoomChallenge challenge = saveChallenge(fixture.room(), 5);
            authenticateAs(fixture.member().getId());

            List<MockHttpServletRequestBuilder> hostOnly = List.of(
                    patch("/api/study-room/{roomId}", fixture.roomId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new StudyRoomUpdateRequest("멤버 수정"))),
                    delete("/api/study-room/{roomId}", fixture.roomId()),
                    patch("/api/study-room/{roomId}/thumbnail-url", fixture.roomId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new ThumbnailUrlUpdateRequest("https://cdn.example.com/x.png"))),
                    delete("/api/study-room/{roomId}/challenges/{challengeId}", fixture.roomId(), challenge.getId()),
                    delete("/api/study-room/{roomId}/members/{memberId}", fixture.roomId(), third.getId())
            );

            for (MockHttpServletRequestBuilder request : hostOnly) {
                mockMvc.perform(request)
                        .andExpect(status().isForbidden())
                        .andExpect(jsonPath("$.errorCode").value(10003));
            }

            assertThat(roomRepository.findById(fixture.roomId())).as("남아 있는 방").isPresent();
            assertThat(challengeRepository.findById(challenge.getId())).as("남아 있는 챌린지").isPresent();
            assertThat(memberRepository.existsByRoomIdAndUserId(fixture.roomId(), third.getId()))
                    .as("강퇴되지 않은 멤버").isTrue();
        }
    }
}
