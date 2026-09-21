package com.aisip.OnO.backend.studyroom.integration;

import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.studyroom.dto.StudyRoomDtos.ReactionToggleRequest;
import com.aisip.OnO.backend.studyroom.dto.StudyRoomDtos.SharedProblemCreateRequest;
import com.aisip.OnO.backend.studyroom.entity.StudyRoom;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomSharedProblem;
import com.aisip.OnO.backend.studyroom.support.StudyRoomTestSupport;
import com.aisip.OnO.backend.user.entity.User;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("스터디룸 공유 문제 API")
class StudyRoomSharedProblemApiTest extends StudyRoomTestSupport {

    @Nested
    @DisplayName("공유")
    class ShareProblem {

        @Test
        @DisplayName("멤버는 자기 문제를 공유할 수 있다")
        void memberSharesOwnProblem() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            Problem problem = saveProblem(fixture.member().getId());
            authenticateAs(fixture.member().getId());

            share(fixture.roomId(), problem.getId(), "같이 봐요")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.problemId").value(problem.getId()))
                    .andExpect(jsonPath("$.data.sharedByUserId").value(fixture.member().getId()))
                    .andExpect(jsonPath("$.data.comment").value("같이 봐요"))
                    .andExpect(jsonPath("$.data.commentCount").value(0))
                    .andExpect(jsonPath("$.data.reactions").isEmpty());

            assertThat(sharedProblemRepository.findAll()).as("저장된 공유 문제").hasSize(1);
        }

        @Test
        @DisplayName("코멘트 없이도 공유할 수 있고 출처가 없으면 기본 문구가 온다")
        void shareWithoutComment() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            Problem problem = problemRepository.saveAndFlush(Problem.from(
                    new com.aisip.OnO.backend.problem.dto.ProblemRegisterDto(
                            null, "메모", null, null, java.time.LocalDateTime.now()),
                    fixture.host().getId()));
            authenticateAs(fixture.host().getId());

            share(fixture.roomId(), problem.getId(), null)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.comment").doesNotExist())
                    .andExpect(jsonPath("$.data.reference").value("공유 문제"));
        }

        @Test
        @DisplayName("코멘트 100자는 허용되고 101자는 400 으로 거절된다")
        void commentLengthBoundaryIsHundred() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            authenticateAs(fixture.host().getId());

            share(fixture.roomId(), saveProblem(fixture.host().getId()).getId(), repeat('가', 100))
                    .andExpect(status().isCreated());

            share(fixture.roomId(), saveProblem(fixture.host().getId()).getId(), repeat('가', 101))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value(10016));
        }

        @Test
        @DisplayName("problemId 가 없으면 400 이다")
        void nullProblemIdIsRejected() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            authenticateAs(fixture.host().getId());

            share(fixture.roomId(), null, "코멘트")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value(10016));
        }

        @Test
        @DisplayName("남의 문제를 공유하려 하면 403 이다")
        void sharingSomeoneElsesProblemIsForbidden() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            Problem hostProblem = saveProblem(fixture.host().getId());
            authenticateAs(fixture.member().getId());

            share(fixture.roomId(), hostProblem.getId(), "남의 문제")
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value(4002));

            assertThat(sharedProblemRepository.findAll()).as("거절된 뒤 저장된 공유 문제").isEmpty();
        }

        @Test
        @DisplayName("존재하지 않는 문제는 404 다")
        void unknownProblemIsNotFound() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            Problem problem = saveProblem(fixture.host().getId());
            authenticateAs(fixture.host().getId());

            share(fixture.roomId(), problem.getId() + 1_000L, "없는 문제")
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value(4001));
        }

        @Test
        @DisplayName("삭제된 문제는 공유할 수 없다")
        void deletedProblemCannotBeShared() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            Problem problem = saveProblem(fixture.host().getId());
            problemRepository.delete(problem);
            problemRepository.flush();
            authenticateAs(fixture.host().getId());

            share(fixture.roomId(), problem.getId(), "삭제된 문제")
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value(4001));
        }

        @Test
        @DisplayName("같은 문제를 같은 방에 두 번 공유하면 409 다")
        void duplicateShareIsRejected() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            Problem problem = saveProblem(fixture.host().getId());
            authenticateAs(fixture.host().getId());
            share(fixture.roomId(), problem.getId(), "첫 공유").andExpect(status().isCreated());

            share(fixture.roomId(), problem.getId(), "두 번째 공유")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value(10020));

            assertThat(sharedProblemRepository.countByRoomId(fixture.roomId()))
                    .as("중복 공유 후 개수").isEqualTo(1);
        }

        @Test
        @DisplayName("같은 문제라도 다른 방에는 공유할 수 있다")
        void sameProblemCanBeSharedToDifferentRooms() throws Exception {
            User host = fixtures.createUser("host");
            StudyRoom first = createRoom(host, "첫방");
            StudyRoom second = createRoom(host, "둘째방");
            Problem problem = saveProblem(host.getId());
            authenticateAs(host.getId());

            share(first.getId(), problem.getId(), null).andExpect(status().isCreated());
            share(second.getId(), problem.getId(), null).andExpect(status().isCreated());

            assertThat(sharedProblemRepository.findAll()).as("두 방에 각각 공유된 결과").hasSize(2);
        }

        @Test
        @DisplayName("비멤버는 공유할 수 없다")
        void nonMemberCannotShare() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            Problem problem = saveProblem(fixture.outsider().getId());
            authenticateAs(fixture.outsider().getId());

            share(fixture.roomId(), problem.getId(), "몰래 공유")
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value(10002));

            assertThat(sharedProblemRepository.findAll()).as("거절된 뒤 저장된 공유 문제").isEmpty();
        }

        @Test
        @DisplayName("인증 없이 공유하면 401 이다")
        void unauthenticatedIsRejected() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            Problem problem = saveProblem(fixture.host().getId());
            clearAuthentication();

            share(fixture.roomId(), problem.getId(), null)
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("목록 조회")
    class GetSharedProblems {

        @Test
        @DisplayName("멤버는 공유 문제 목록을 최신순으로 볼 수 있다")
        void memberSeesLatestFirst() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            StudyRoomSharedProblem first = saveSharedProblem(fixture.room(), fixture.host(),
                    saveProblem(fixture.host().getId()), "첫 번째");
            StudyRoomSharedProblem second = saveSharedProblem(fixture.room(), fixture.member(),
                    saveProblem(fixture.member().getId()), "두 번째");
            authenticateAs(fixture.member().getId());

            mockMvc.perform(get("/api/study-room/{roomId}/shared-problems", fixture.roomId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content.length()").value(2))
                    .andExpect(jsonPath("$.data.content[0].sharedProblemId").value(second.getId()))
                    .andExpect(jsonPath("$.data.content[1].sharedProblemId").value(first.getId()));
        }

        @Test
        @DisplayName("공유 문제가 없으면 빈 목록을 준다")
        void emptyList() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            authenticateAs(fixture.member().getId());

            mockMvc.perform(get("/api/study-room/{roomId}/shared-problems", fixture.roomId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content").isEmpty())
                    .andExpect(jsonPath("$.data.hasNext").value(false));
        }

        @Test
        @DisplayName("커서로 다음 페이지를 이어서 받는다")
        void cursorPagination() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            List<Long> ids = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                ids.add(saveSharedProblem(fixture.room(), fixture.host(),
                        saveProblem(fixture.host().getId()), "코멘트" + i).getId());
            }
            authenticateAs(fixture.host().getId());

            MvcResult firstPage = mockMvc.perform(get("/api/study-room/{roomId}/shared-problems", fixture.roomId())
                            .param("size", "2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content.length()").value(2))
                    .andExpect(jsonPath("$.data.hasNext").value(true))
                    .andReturn();
            Number cursor = JsonPath.read(firstPage.getResponse().getContentAsString(), "$.data.nextCursor");

            mockMvc.perform(get("/api/study-room/{roomId}/shared-problems", fixture.roomId())
                            .param("size", "2")
                            .param("cursor", String.valueOf(cursor.longValue())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[0].sharedProblemId").value(ids.get(2)));
        }

        @Test
        @DisplayName("댓글 수와 리액션 요약이 함께 온다")
        void listIncludesCommentCountAndReactions() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            StudyRoomSharedProblem shared = saveSharedProblem(fixture.room(), fixture.host(),
                    saveProblem(fixture.host().getId()), "코멘트");
            saveComment(shared, fixture.member(), "댓글 1");
            saveComment(shared, fixture.member(), "댓글 2");
            sharedProblemReactionRepository.saveAndFlush(
                    com.aisip.OnO.backend.studyroom.entity.StudyRoomSharedProblemReaction
                            .create(shared, fixture.member(), EMOJI));
            authenticateAs(fixture.member().getId());

            mockMvc.perform(get("/api/study-room/{roomId}/shared-problems", fixture.roomId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[0].commentCount").value(2))
                    .andExpect(jsonPath("$.data.content[0].reactions[0].emoji").value(EMOJI))
                    .andExpect(jsonPath("$.data.content[0].reactions[0].reactedByMe").value(true));
        }

        @Test
        @DisplayName("다른 방의 공유 문제는 섞이지 않는다")
        void listIsScopedToRoom() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            StudyRoom otherRoom = createRoom(fixture.outsider(), "남의 방");
            saveSharedProblem(otherRoom, fixture.outsider(),
                    saveProblem(fixture.outsider().getId()), "남의 공유");
            authenticateAs(fixture.host().getId());

            mockMvc.perform(get("/api/study-room/{roomId}/shared-problems", fixture.roomId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content").isEmpty());
        }

        @Test
        @DisplayName("비멤버는 공유 문제 목록을 볼 수 없다")
        void nonMemberCannotRead() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            saveSharedProblem(fixture.room(), fixture.host(), saveProblem(fixture.host().getId()), null);
            authenticateAs(fixture.outsider().getId());

            mockMvc.perform(get("/api/study-room/{roomId}/shared-problems", fixture.roomId()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value(10002));
        }

        @Test
        @DisplayName("인증 없이 조회하면 401 이다")
        void unauthenticatedIsRejected() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            clearAuthentication();

            mockMvc.perform(get("/api/study-room/{roomId}/shared-problems", fixture.roomId()))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("리액션 토글")
    class ToggleReaction {

        @Test
        @DisplayName("멤버는 리액션을 달고 다시 눌러 취소할 수 있다")
        void toggleAddsThenRemoves() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            StudyRoomSharedProblem shared = saveSharedProblem(fixture.room(), fixture.host(),
                    saveProblem(fixture.host().getId()), null);
            authenticateAs(fixture.member().getId());

            toggle(fixture.roomId(), shared.getId(), EMOJI)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.sharedProblemId").value(shared.getId()))
                    .andExpect(jsonPath("$.data.reactions[0].count").value(1))
                    .andExpect(jsonPath("$.data.reactions[0].reactedByMe").value(true));

            toggle(fixture.roomId(), shared.getId(), EMOJI)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.reactions").isEmpty());
        }

        @Test
        @DisplayName("토글은 남의 리액션을 지우지 않는다")
        void toggleNeverDeletesOthersReaction() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            StudyRoomSharedProblem shared = saveSharedProblem(fixture.room(), fixture.host(),
                    saveProblem(fixture.host().getId()), null);
            sharedProblemReactionRepository.saveAndFlush(
                    com.aisip.OnO.backend.studyroom.entity.StudyRoomSharedProblemReaction
                            .create(shared, fixture.host(), EMOJI));
            authenticateAs(fixture.member().getId());

            toggle(fixture.roomId(), shared.getId(), EMOJI)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.reactions[0].count").value(2));
            toggle(fixture.roomId(), shared.getId(), EMOJI)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.reactions[0].count").value(1));

            assertThat(sharedProblemReactionRepository.findAllBySharedProblemId(shared.getId()))
                    .as("공유자의 리액션은 남아 있다")
                    .singleElement()
                    .satisfies(reaction -> assertThat(reaction.getUser().getId()).isEqualTo(fixture.host().getId()));
        }

        @ParameterizedTest(name = "이모지 = \"{0}\"")
        @ValueSource(strings = {"not_allowed", "🔥", "GOLD_MEDAL"})
        @DisplayName("허용 목록에 없는 이모지는 400 이다")
        void disallowedEmojiIsRejected(String emoji) throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            StudyRoomSharedProblem shared = saveSharedProblem(fixture.room(), fixture.host(),
                    saveProblem(fixture.host().getId()), null);
            authenticateAs(fixture.host().getId());

            toggle(fixture.roomId(), shared.getId(), emoji)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value(11001));
        }

        @Test
        @DisplayName("존재하지 않는 공유 문제에는 리액션할 수 없다")
        void unknownSharedProblemIsNotFound() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            authenticateAs(fixture.host().getId());

            toggle(fixture.roomId(), nonExistentSharedProblemId(), EMOJI)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value(10013));
        }

        @Test
        @DisplayName("다른 방의 공유 문제 ID 로는 리액션할 수 없다")
        void sharedProblemFromAnotherRoomIsNotFound() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            StudyRoom otherRoom = createRoom(fixture.outsider(), "남의 방");
            StudyRoomSharedProblem otherShared = saveSharedProblem(otherRoom, fixture.outsider(),
                    saveProblem(fixture.outsider().getId()), null);
            authenticateAs(fixture.host().getId());

            toggle(fixture.roomId(), otherShared.getId(), EMOJI)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value(10013));
        }

        @Test
        @DisplayName("비멤버는 리액션할 수 없다")
        void nonMemberCannotReact() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            StudyRoomSharedProblem shared = saveSharedProblem(fixture.room(), fixture.host(),
                    saveProblem(fixture.host().getId()), null);
            authenticateAs(fixture.outsider().getId());

            toggle(fixture.roomId(), shared.getId(), EMOJI)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value(10002));

            assertThat(sharedProblemReactionRepository.findAllBySharedProblemId(shared.getId()))
                    .as("비멤버 리액션은 저장되지 않는다").isEmpty();
        }
    }

    @Nested
    @DisplayName("삭제")
    class DeleteSharedProblem {

        @Test
        @DisplayName("공유한 본인은 삭제할 수 있고 댓글·리액션까지 함께 지워진다")
        void sharerDeletesWithCascade() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            StudyRoomSharedProblem shared = saveSharedProblem(fixture.room(), fixture.host(),
                    saveProblem(fixture.host().getId()), "코멘트");
            var comment = saveComment(shared, fixture.member(), "댓글");
            commentReactionRepository.saveAndFlush(
                    com.aisip.OnO.backend.studyroom.entity.StudyRoomSharedProblemCommentReaction
                            .create(comment, fixture.member(), EMOJI));
            sharedProblemReactionRepository.saveAndFlush(
                    com.aisip.OnO.backend.studyroom.entity.StudyRoomSharedProblemReaction
                            .create(shared, fixture.member(), EMOJI));
            authenticateAs(fixture.host().getId());

            mockMvc.perform(delete("/api/study-room/{roomId}/shared-problems/{sharedProblemId}",
                            fixture.roomId(), shared.getId()))
                    .andExpect(status().isOk());

            assertThat(sharedProblemRepository.findById(shared.getId())).as("삭제된 공유 문제").isEmpty();
            assertThat(commentRepository.findById(comment.getId())).as("함께 삭제된 댓글").isEmpty();
            assertThat(commentReactionRepository.findAllByCommentId(comment.getId()))
                    .as("함께 삭제된 댓글 리액션").isEmpty();
            assertThat(sharedProblemReactionRepository.findAllBySharedProblemId(shared.getId()))
                    .as("함께 삭제된 공유 문제 리액션").isEmpty();
        }

        @Test
        @DisplayName("원본 문제는 공유 문제를 지워도 남는다")
        void originalProblemSurvives() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            Problem problem = saveProblem(fixture.host().getId());
            StudyRoomSharedProblem shared = saveSharedProblem(fixture.room(), fixture.host(), problem, null);
            authenticateAs(fixture.host().getId());

            mockMvc.perform(delete("/api/study-room/{roomId}/shared-problems/{sharedProblemId}",
                            fixture.roomId(), shared.getId()))
                    .andExpect(status().isOk());

            assertThat(problemRepository.findById(problem.getId())).as("남아 있는 원본 문제").isPresent();
        }

        @Test
        @DisplayName("공유자가 아닌 멤버는 삭제할 수 없다")
        void nonSharerMemberCannotDelete() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            StudyRoomSharedProblem shared = saveSharedProblem(fixture.room(), fixture.host(),
                    saveProblem(fixture.host().getId()), null);
            authenticateAs(fixture.member().getId());

            mockMvc.perform(delete("/api/study-room/{roomId}/shared-problems/{sharedProblemId}",
                            fixture.roomId(), shared.getId()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value(10002));

            assertThat(sharedProblemRepository.findById(shared.getId())).as("남아 있는 공유 문제").isPresent();
        }

        @Test
        @DisplayName("방장이라도 남이 공유한 문제는 삭제할 수 없다")
        void hostCannotDeleteOthersSharedProblem() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            StudyRoomSharedProblem shared = saveSharedProblem(fixture.room(), fixture.member(),
                    saveProblem(fixture.member().getId()), null);
            authenticateAs(fixture.host().getId());

            mockMvc.perform(delete("/api/study-room/{roomId}/shared-problems/{sharedProblemId}",
                            fixture.roomId(), shared.getId()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value(10002));
        }

        @Test
        @DisplayName("비멤버는 삭제할 수 없다")
        void nonMemberCannotDelete() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            StudyRoomSharedProblem shared = saveSharedProblem(fixture.room(), fixture.host(),
                    saveProblem(fixture.host().getId()), null);
            authenticateAs(fixture.outsider().getId());

            mockMvc.perform(delete("/api/study-room/{roomId}/shared-problems/{sharedProblemId}",
                            fixture.roomId(), shared.getId()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value(10002));

            assertThat(sharedProblemRepository.findById(shared.getId())).as("남아 있는 공유 문제").isPresent();
        }

        @Test
        @DisplayName("존재하지 않는 공유 문제는 404 다")
        void unknownSharedProblemIsNotFound() throws Exception {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            authenticateAs(fixture.host().getId());

            mockMvc.perform(delete("/api/study-room/{roomId}/shared-problems/{sharedProblemId}",
                            fixture.roomId(), nonExistentSharedProblemId()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value(10013));
        }
    }

    private ResultActions share(Long roomId, Long problemId, String comment) throws Exception {
        return mockMvc.perform(post("/api/study-room/{roomId}/shared-problems", roomId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new SharedProblemCreateRequest(problemId, comment))));
    }

    private ResultActions toggle(Long roomId, Long sharedProblemId, String emoji) throws Exception {
        return mockMvc.perform(post("/api/study-room/{roomId}/shared-problems/{sharedProblemId}/reactions",
                        roomId, sharedProblemId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ReactionToggleRequest(emoji))));
    }
}
