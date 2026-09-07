package com.aisip.OnO.backend.studyroom.exception;

import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.studyroom.dto.StudyRoomDtos.*;
import com.aisip.OnO.backend.studyroom.entity.*;
import com.aisip.OnO.backend.studyroom.service.StudyRoomService;
import com.aisip.OnO.backend.studyroom.support.StudyRoomTestSupport;
import com.aisip.OnO.backend.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link StudyRoomErrorCase} 각 값이 실제로 어떤 요청에서 나오는지 고정한다.
 *
 * <p>에러 코드는 앱이 분기하는 계약이다. 코드를 유지한 채 상태 코드만 바뀌거나, 반대로
 * 같은 상황에서 다른 코드가 나가기 시작하면 클라이언트가 조용히 어긋난다. enum 값마다
 * 그 값을 내는 최소 시나리오를 하나씩 붙여 둔다.
 *
 * <p>도메인 전체에서의 errorCode 유일성은 {@code ErrorCaseContractTest} 가 따로 검사한다.
 */
@DisplayName("StudyRoomErrorCase 발생 시나리오")
class StudyRoomErrorCaseScenarioTest extends StudyRoomTestSupport {

    /**
     * 프로덕션 코드에서 더 이상 던지지 않는 값들.
     *
     * <ul>
     *   <li>{@code SESSION_ALREADY_ACTIVE}, {@code SESSION_NOT_FOUND} — 공부 세션 기능이 제거되면서 남은 값</li>
     *   <li>{@code INVALID_REACTION_EMOJI} — 이모지 검증이 공통 {@code CustomEmojiValidator} 로 옮겨가면서
     *       실제 응답은 11001(INVALID_EMOJI_KEY)로 나간다</li>
     * </ul>
     */
    private static final List<StudyRoomErrorCase> UNREACHABLE = List.of(
            StudyRoomErrorCase.SESSION_ALREADY_ACTIVE,
            StudyRoomErrorCase.SESSION_NOT_FOUND,
            StudyRoomErrorCase.INVALID_REACTION_EMOJI
    );

    @Test
    @DisplayName("현재 발생 경로가 없는 값은 세 개뿐이고 그 목록은 고정되어 있다")
    void unreachableCasesAreKnown() {
        assertThat(Arrays.stream(StudyRoomErrorCase.values()).toList())
                .as("전체 에러 케이스")
                .hasSize(20)
                .containsAll(UNREACHABLE);
        assertThat(UNREACHABLE)
                .as("발생 경로가 없는 값 — 새로 쓰기 시작하면 시나리오 테스트를 추가한다")
                .extracting(StudyRoomErrorCase::getErrorCode)
                .containsExactly(10011, 10012, 10015);
    }

    @Nested
    @DisplayName("방과 멤버십")
    class RoomAndMembership {

        @Test
        @DisplayName("10001 STUDY_ROOM_NOT_FOUND — 없는 방 조회")
        void studyRoomNotFound() throws Exception {
            User user = fixtures.createUser("user");
            authenticateAs(user.getId());

            expect(mockMvc.perform(get("/api/study-room/{roomId}", nonExistentRoomId())),
                    StudyRoomErrorCase.STUDY_ROOM_NOT_FOUND);
        }

        @Test
        @DisplayName("10002 STUDY_ROOM_FORBIDDEN — 비멤버의 방 조회")
        void studyRoomForbidden() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            authenticateAs(fixture.outsider().getId());

            expect(mockMvc.perform(get("/api/study-room/{roomId}", fixture.roomId())),
                    StudyRoomErrorCase.STUDY_ROOM_FORBIDDEN);
        }

        @Test
        @DisplayName("10003 STUDY_ROOM_HOST_ONLY — 일반 멤버의 방 삭제")
        void studyRoomHostOnly() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            authenticateAs(fixture.member().getId());

            expect(mockMvc.perform(delete("/api/study-room/{roomId}", fixture.roomId())),
                    StudyRoomErrorCase.STUDY_ROOM_HOST_ONLY);
        }

        @Test
        @DisplayName("10005 STUDY_ROOM_LIMIT_EXCEEDED — 11번째 방 생성")
        void studyRoomLimitExceeded() throws Exception {
            User user = fixtures.createUser("user");
            for (int i = 0; i < StudyRoomService.MAX_USER_ROOM_COUNT; i++) {
                createRoom(user, "방" + i);
            }
            authenticateAs(user.getId());

            expect(mockMvc.perform(post("/api/study-room")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new StudyRoomCreateRequest("초과방")))),
                    StudyRoomErrorCase.STUDY_ROOM_LIMIT_EXCEEDED);
        }

        @Test
        @DisplayName("10016 INVALID_STUDY_ROOM_REQUEST — 20자를 넘는 방 이름")
        void invalidStudyRoomRequest() throws Exception {
            User user = fixtures.createUser("user");
            authenticateAs(user.getId());

            expect(mockMvc.perform(post("/api/study-room")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new StudyRoomCreateRequest(repeat('가', 21))))),
                    StudyRoomErrorCase.INVALID_STUDY_ROOM_REQUEST);
        }
    }

    @Nested
    @DisplayName("초대와 참여")
    class Invite {

        @Test
        @DisplayName("10004 STUDY_ROOM_FULL — 정원이 찬 방 참여")
        void studyRoomFull() throws Exception {
            User host = fixtures.createUser("host");
            StudyRoom room = createRoom(host, "정원방");
            for (int i = 1; i < StudyRoomService.MAX_ROOM_MEMBER_COUNT; i++) {
                addMember(room, fixtures.createUser("filler" + i));
            }
            saveValidInviteCode(room, "123456");
            User latecomer = fixtures.createUser("latecomer");
            authenticateAs(latecomer.getId());

            expect(join("123456"), StudyRoomErrorCase.STUDY_ROOM_FULL);
        }

        @Test
        @DisplayName("10006 INVITE_CODE_INVALID — 형식이 맞지 않는 코드")
        void inviteCodeInvalid() throws Exception {
            User user = fixtures.createUser("user");
            authenticateAs(user.getId());

            expect(join("abcdef"), StudyRoomErrorCase.INVITE_CODE_INVALID);
        }

        @Test
        @DisplayName("10007 INVITE_CODE_EXPIRED — 만료된 코드")
        void inviteCodeExpired() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            saveInviteCode(fixture.room(), "123456", LocalDateTime.now().minusSeconds(1));
            authenticateAs(fixture.outsider().getId());

            expect(join("123456"), StudyRoomErrorCase.INVITE_CODE_EXPIRED);
        }

        @Test
        @DisplayName("10008 ALREADY_MEMBER — 이미 참여한 방에 재참여")
        void alreadyMember() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            saveValidInviteCode(fixture.room(), "123456");
            authenticateAs(fixture.member().getId());

            expect(join("123456"), StudyRoomErrorCase.ALREADY_MEMBER);
        }
    }

    @Nested
    @DisplayName("챌린지")
    class Challenge {

        @Test
        @DisplayName("10009 CHALLENGE_NOT_FOUND — 없는 챌린지 삭제")
        void challengeNotFound() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            authenticateAs(fixture.host().getId());

            expect(mockMvc.perform(delete("/api/study-room/{roomId}/challenges/{challengeId}",
                            fixture.roomId(), nonExistentChallengeId())),
                    StudyRoomErrorCase.CHALLENGE_NOT_FOUND);
        }

        @Test
        @DisplayName("10010 CHALLENGE_LIMIT_EXCEEDED — 여섯 번째 진행 중 챌린지 생성")
        void challengeLimitExceeded() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            for (int i = 0; i < 5; i++) {
                saveChallenge(fixture.room(), "챌린지" + i, StudyRoomChallengeType.INDIVIDUAL,
                        StudyRoomChallengeMetric.PROBLEM_COUNT, null, null, 1_000,
                        LocalDateTime.now().minusHours(1), LocalDateTime.now().plusDays(7));
            }
            authenticateAs(fixture.host().getId());

            expect(mockMvc.perform(post("/api/study-room/{roomId}/challenges", fixture.roomId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new ChallengeCreateRequest(
                                    "여섯 번째", "individual", "problem_count", null, null, 1_000,
                                    null, LocalDateTime.now().plusDays(7))))),
                    StudyRoomErrorCase.CHALLENGE_LIMIT_EXCEEDED);
        }
    }

    @Nested
    @DisplayName("공유 문제와 댓글")
    class SharedProblemAndComment {

        @Test
        @DisplayName("10013 SHARED_PROBLEM_NOT_FOUND — 없는 공유 문제 삭제")
        void sharedProblemNotFound() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            authenticateAs(fixture.host().getId());

            expect(mockMvc.perform(delete("/api/study-room/{roomId}/shared-problems/{id}",
                            fixture.roomId(), nonExistentSharedProblemId())),
                    StudyRoomErrorCase.SHARED_PROBLEM_NOT_FOUND);
        }

        @Test
        @DisplayName("10020 ALREADY_SHARED_PROBLEM — 같은 문제를 같은 방에 다시 공유")
        void alreadySharedProblem() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            Problem problem = saveProblem(fixture.host().getId());
            saveSharedProblem(fixture.room(), fixture.host(), problem, null);
            authenticateAs(fixture.host().getId());

            expect(mockMvc.perform(post("/api/study-room/{roomId}/shared-problems", fixture.roomId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new SharedProblemCreateRequest(problem.getId(), null)))),
                    StudyRoomErrorCase.ALREADY_SHARED_PROBLEM);
        }

        @Test
        @DisplayName("10017 SHARED_PROBLEM_COMMENT_NOT_FOUND — 없는 댓글 삭제")
        void sharedProblemCommentNotFound() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            StudyRoomSharedProblem shared = saveSharedProblem(fixture.room(), fixture.host(),
                    saveProblem(fixture.host().getId()), null);
            authenticateAs(fixture.host().getId());

            expect(mockMvc.perform(delete("/api/study-room/{roomId}/shared-problems/{id}/comments/{commentId}",
                            fixture.roomId(), shared.getId(), nonExistentCommentId())),
                    StudyRoomErrorCase.SHARED_PROBLEM_COMMENT_NOT_FOUND);
        }

        @Test
        @DisplayName("10018 INVALID_SHARED_PROBLEM_COMMENT — 빈 댓글 작성")
        void invalidSharedProblemComment() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            StudyRoomSharedProblem shared = saveSharedProblem(fixture.room(), fixture.host(),
                    saveProblem(fixture.host().getId()), null);
            authenticateAs(fixture.member().getId());

            expect(mockMvc.perform(post("/api/study-room/{roomId}/shared-problems/{id}/comments",
                            fixture.roomId(), shared.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new SharedProblemCommentRequest("   ")))),
                    StudyRoomErrorCase.INVALID_SHARED_PROBLEM_COMMENT);
        }

        @Test
        @DisplayName("10019 SHARED_PROBLEM_COMMENT_FORBIDDEN — 남의 댓글 수정")
        void sharedProblemCommentForbidden() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            StudyRoomSharedProblem shared = saveSharedProblem(fixture.room(), fixture.host(),
                    saveProblem(fixture.host().getId()), null);
            StudyRoomSharedProblemComment comment = saveComment(shared, fixture.member(), "멤버 댓글");
            authenticateAs(fixture.host().getId());

            expect(mockMvc.perform(patch("/api/study-room/{roomId}/shared-problems/{id}/comments/{commentId}",
                            fixture.roomId(), shared.getId(), comment.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new SharedProblemCommentRequest("고침")))),
                    StudyRoomErrorCase.SHARED_PROBLEM_COMMENT_FORBIDDEN);
        }
    }

    @Nested
    @DisplayName("주간 리포트")
    class WeeklyReport {

        @Test
        @DisplayName("10014 REPORT_NOT_FOUND — 없는 리포트 읽음 처리")
        void reportNotFound() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            authenticateAs(fixture.member().getId());

            expect(mockMvc.perform(patch("/api/study-room/{roomId}/weekly-reports/{reportId}/read",
                            fixture.roomId(), nonExistentReportId())),
                    StudyRoomErrorCase.REPORT_NOT_FOUND);
        }

        @Test
        @DisplayName("리포트가 존재하면 같은 요청이 정상 처리된다 — 404 가 방 소속 때문이 아님을 확인")
        void existingReportSucceeds() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            StudyRoomWeeklyReport report = saveWeeklyReport(fixture.room(), LocalDate.of(2026, 1, 5));
            authenticateAs(fixture.member().getId());

            mockMvc.perform(patch("/api/study-room/{roomId}/weekly-reports/{reportId}/read",
                            fixture.roomId(), report.getId()))
                    .andExpect(status().isOk());
        }
    }

    private void expect(ResultActions actions, StudyRoomErrorCase errorCase) throws Exception {
        actions.andExpect(status().is(errorCase.getHttpStatusCode()))
                .andExpect(jsonPath("$.errorCode").value(errorCase.getErrorCode()))
                .andExpect(jsonPath("$.message").value(errorCase.getMessage()));
    }

    private ResultActions join(String code) throws Exception {
        return mockMvc.perform(post("/api/study-room/join")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new StudyRoomJoinRequest(code))));
    }
}
