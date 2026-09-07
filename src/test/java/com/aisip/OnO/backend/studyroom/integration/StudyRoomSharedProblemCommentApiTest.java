package com.aisip.OnO.backend.studyroom.integration;

import com.aisip.OnO.backend.studyroom.dto.StudyRoomDtos.ReactionToggleRequest;
import com.aisip.OnO.backend.studyroom.dto.StudyRoomDtos.SharedProblemCommentRequest;
import com.aisip.OnO.backend.studyroom.entity.StudyRoom;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomSharedProblem;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomSharedProblemComment;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomSharedProblemCommentReaction;
import com.aisip.OnO.backend.studyroom.support.StudyRoomTestSupport;
import com.aisip.OnO.backend.user.entity.User;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
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

@DisplayName("스터디룸 공유 문제 댓글 API")
class StudyRoomSharedProblemCommentApiTest extends StudyRoomTestSupport {

    /**
     * 서비스가 강제하는 댓글 최대 길이.
     *
     * <p>엔티티 컬럼은 {@code varchar(1000)} 인데 서비스는 300자에서 자른다.
     * 컬럼보다 좁은 쪽이 먼저 걸리므로 Data truncation(500)이 아니라 400 으로 나가야 한다.
     */
    private static final int MAX_COMMENT_LENGTH = 300;

    private RoomFixture fixture;
    private StudyRoomSharedProblem sharedProblem;

    @BeforeEach
    void setUpSharedProblem() {
        fixture = createRoomWithMemberAndOutsider();
        sharedProblem = saveSharedProblem(fixture.room(), fixture.host(),
                saveProblem(fixture.host().getId()), "공유 코멘트");
    }

    @Nested
    @DisplayName("작성")
    class CreateComment {

        @Test
        @DisplayName("멤버는 댓글을 달 수 있고 작성 직후에는 수정 표시가 없다")
        void memberCreatesComment() throws Exception {
            authenticateAs(fixture.member().getId());

            createComment("좋은 문제네요")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.content").value("좋은 문제네요"))
                    .andExpect(jsonPath("$.data.authorId").value(fixture.member().getId()))
                    .andExpect(jsonPath("$.data.isMine").value(true))
                    .andExpect(jsonPath("$.data.isEdited").value(false))
                    .andExpect(jsonPath("$.data.reactions").isEmpty());

            assertThat(commentRepository.findAll()).as("저장된 댓글").hasSize(1);
        }

        @Test
        @DisplayName("댓글 내용 앞뒤 공백은 잘린다")
        void contentIsTrimmed() throws Exception {
            authenticateAs(fixture.member().getId());

            createComment("  공백 댓글  ")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.content").value("공백 댓글"));
        }

        @Test
        @DisplayName("300자는 허용되고 301자는 400 으로 거절된다")
        void lengthBoundary() throws Exception {
            authenticateAs(fixture.member().getId());

            createComment(repeat('가', MAX_COMMENT_LENGTH))
                    .andExpect(status().isCreated());

            createComment(repeat('가', MAX_COMMENT_LENGTH + 1))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value(10018));
        }

        @Test
        @DisplayName("컬럼 길이(1000자)에 해당하는 입력도 500 이 아니라 400 으로 거절된다")
        void columnLengthInputIsRejectedWithBadRequest() throws Exception {
            authenticateAs(fixture.member().getId());

            createComment(repeat('가', 1000))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value(10018));

            assertThat(commentRepository.findAll()).as("거절된 뒤 저장된 댓글").isEmpty();
        }

        @ParameterizedTest(name = "내용 = \"{0}\"")
        @ValueSource(strings = {"", " ", "   ", "\n", "\t"})
        @DisplayName("빈 내용은 400 으로 거절된다")
        void blankContentIsRejected(String content) throws Exception {
            authenticateAs(fixture.member().getId());

            createComment(content)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value(10018));
        }

        @Test
        @DisplayName("내용이 null 이면 400 이다")
        void nullContentIsRejected() throws Exception {
            authenticateAs(fixture.member().getId());

            mockMvc.perform(post("/api/study-room/{roomId}/shared-problems/{sharedProblemId}/comments",
                            fixture.roomId(), sharedProblem.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value(10018));
        }

        @Test
        @DisplayName("존재하지 않는 공유 문제에는 댓글을 달 수 없다")
        void unknownSharedProblemIsNotFound() throws Exception {
            authenticateAs(fixture.member().getId());

            mockMvc.perform(post("/api/study-room/{roomId}/shared-problems/{sharedProblemId}/comments",
                            fixture.roomId(), nonExistentSharedProblemId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new SharedProblemCommentRequest("댓글"))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value(10013));
        }

        @Test
        @DisplayName("다른 방의 공유 문제에는 댓글을 달 수 없다")
        void sharedProblemFromAnotherRoomIsNotFound() throws Exception {
            StudyRoom otherRoom = createRoom(fixture.outsider(), "남의 방");
            StudyRoomSharedProblem otherShared = saveSharedProblem(otherRoom, fixture.outsider(),
                    saveProblem(fixture.outsider().getId()), null);
            authenticateAs(fixture.host().getId());

            mockMvc.perform(post("/api/study-room/{roomId}/shared-problems/{sharedProblemId}/comments",
                            fixture.roomId(), otherShared.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new SharedProblemCommentRequest("댓글"))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value(10013));

            assertThat(commentRepository.findAll()).as("남의 방 공유 문제에 달린 댓글").isEmpty();
        }

        @Test
        @DisplayName("비멤버는 댓글을 달 수 없다")
        void nonMemberCannotComment() throws Exception {
            authenticateAs(fixture.outsider().getId());

            createComment("몰래 댓글")
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value(10002));

            assertThat(commentRepository.findAll()).as("거절된 뒤 저장된 댓글").isEmpty();
        }

        @Test
        @DisplayName("인증 없이 댓글을 달면 401 이다")
        void unauthenticatedIsRejected() throws Exception {
            clearAuthentication();

            createComment("몰래 댓글").andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("목록 조회")
    class GetComments {

        @Test
        @DisplayName("최신 댓글이 먼저 오고 방장에게는 남의 댓글도 삭제 가능으로 표시된다")
        void latestFirstAndHostCanDeleteOthers() throws Exception {
            StudyRoomSharedProblemComment first = saveComment(sharedProblem, fixture.member(), "첫 댓글");
            StudyRoomSharedProblemComment second = saveComment(sharedProblem, fixture.member(), "둘째 댓글");
            authenticateAs(fixture.host().getId());

            mockMvc.perform(get("/api/study-room/{roomId}/shared-problems/{sharedProblemId}/comments",
                            fixture.roomId(), sharedProblem.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content.length()").value(2))
                    .andExpect(jsonPath("$.data.content[0].commentId").value(second.getId()))
                    .andExpect(jsonPath("$.data.content[1].commentId").value(first.getId()))
                    .andExpect(jsonPath("$.data.content[0].isMine").value(false))
                    .andExpect(jsonPath("$.data.content[0].canDelete").value(true));
        }

        @Test
        @DisplayName("일반 멤버에게 남의 댓글은 삭제 불가로 표시된다")
        void memberCannotDeleteOthersComment() throws Exception {
            saveComment(sharedProblem, fixture.host(), "방장 댓글");
            authenticateAs(fixture.member().getId());

            mockMvc.perform(get("/api/study-room/{roomId}/shared-problems/{sharedProblemId}/comments",
                            fixture.roomId(), sharedProblem.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[0].isMine").value(false))
                    .andExpect(jsonPath("$.data.content[0].canDelete").value(false));
        }

        @Test
        @DisplayName("댓글이 없으면 빈 목록을 준다")
        void emptyList() throws Exception {
            authenticateAs(fixture.member().getId());

            mockMvc.perform(get("/api/study-room/{roomId}/shared-problems/{sharedProblemId}/comments",
                            fixture.roomId(), sharedProblem.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content").isEmpty())
                    .andExpect(jsonPath("$.data.hasNext").value(false));
        }

        @Test
        @DisplayName("커서로 다음 페이지를 이어서 받는다")
        void cursorPagination() throws Exception {
            List<Long> ids = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                ids.add(saveComment(sharedProblem, fixture.member(), "댓글" + i).getId());
            }
            authenticateAs(fixture.member().getId());

            MvcResult firstPage = mockMvc.perform(get("/api/study-room/{roomId}/shared-problems/{sharedProblemId}/comments",
                            fixture.roomId(), sharedProblem.getId())
                            .param("size", "2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content.length()").value(2))
                    .andExpect(jsonPath("$.data.hasNext").value(true))
                    .andReturn();
            Number cursor = JsonPath.read(firstPage.getResponse().getContentAsString(), "$.data.nextCursor");

            mockMvc.perform(get("/api/study-room/{roomId}/shared-problems/{sharedProblemId}/comments",
                            fixture.roomId(), sharedProblem.getId())
                            .param("size", "2")
                            .param("cursor", String.valueOf(cursor.longValue())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[0].commentId").value(ids.get(2)));
        }

        @Test
        @DisplayName("size 는 1 이상 50 이하로 보정된다")
        void sizeIsClamped() throws Exception {
            saveComment(sharedProblem, fixture.member(), "댓글");
            authenticateAs(fixture.member().getId());

            mockMvc.perform(get("/api/study-room/{roomId}/shared-problems/{sharedProblemId}/comments",
                            fixture.roomId(), sharedProblem.getId())
                            .param("size", "0"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.size").value(1));

            mockMvc.perform(get("/api/study-room/{roomId}/shared-problems/{sharedProblemId}/comments",
                            fixture.roomId(), sharedProblem.getId())
                            .param("size", "500"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.size").value(50));
        }

        @Test
        @DisplayName("다른 공유 문제의 댓글은 섞이지 않는다")
        void commentsAreScopedToSharedProblem() throws Exception {
            StudyRoomSharedProblem another = saveSharedProblem(fixture.room(), fixture.host(),
                    saveProblem(fixture.host().getId()), null);
            saveComment(another, fixture.member(), "다른 공유 문제의 댓글");
            authenticateAs(fixture.member().getId());

            mockMvc.perform(get("/api/study-room/{roomId}/shared-problems/{sharedProblemId}/comments",
                            fixture.roomId(), sharedProblem.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content").isEmpty());
        }

        @Test
        @DisplayName("비멤버는 댓글을 볼 수 없다")
        void nonMemberCannotRead() throws Exception {
            saveComment(sharedProblem, fixture.member(), "댓글");
            authenticateAs(fixture.outsider().getId());

            mockMvc.perform(get("/api/study-room/{roomId}/shared-problems/{sharedProblemId}/comments",
                            fixture.roomId(), sharedProblem.getId()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value(10002));
        }

        @Test
        @DisplayName("인증 없이 조회하면 401 이다")
        void unauthenticatedIsRejected() throws Exception {
            clearAuthentication();

            mockMvc.perform(get("/api/study-room/{roomId}/shared-problems/{sharedProblemId}/comments",
                            fixture.roomId(), sharedProblem.getId()))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("수정")
    class UpdateComment {

        @Test
        @DisplayName("작성자는 자기 댓글을 수정할 수 있고 수정 표시가 붙는다")
        void authorCanUpdate() throws Exception {
            StudyRoomSharedProblemComment comment = saveComment(sharedProblem, fixture.member(), "원래 내용");
            authenticateAs(fixture.member().getId());

            updateComment(comment.getId(), "고친 내용")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content").value("고친 내용"))
                    .andExpect(jsonPath("$.data.isEdited").value(true));

            assertThat(commentRepository.findById(comment.getId()).orElseThrow().getContent())
                    .as("영속화된 댓글 내용").isEqualTo("고친 내용");
        }

        @Test
        @DisplayName("작성자가 아닌 멤버는 수정할 수 없다")
        void otherMemberCannotUpdate() throws Exception {
            StudyRoomSharedProblemComment comment = saveComment(sharedProblem, fixture.member(), "원래 내용");
            authenticateAs(fixture.host().getId());

            updateComment(comment.getId(), "방장이 고침")
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value(10019));

            assertThat(commentRepository.findById(comment.getId()).orElseThrow().getContent())
                    .as("수정되지 않은 댓글 내용").isEqualTo("원래 내용");
        }

        @Test
        @DisplayName("비멤버는 수정할 수 없다")
        void nonMemberCannotUpdate() throws Exception {
            StudyRoomSharedProblemComment comment = saveComment(sharedProblem, fixture.member(), "원래 내용");
            authenticateAs(fixture.outsider().getId());

            updateComment(comment.getId(), "외부인이 고침")
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value(10002));
        }

        @Test
        @DisplayName("301자로는 수정할 수 없다")
        void lengthBoundaryOnUpdate() throws Exception {
            StudyRoomSharedProblemComment comment = saveComment(sharedProblem, fixture.member(), "원래 내용");
            authenticateAs(fixture.member().getId());

            updateComment(comment.getId(), repeat('가', MAX_COMMENT_LENGTH + 1))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value(10018));
        }

        @Test
        @DisplayName("존재하지 않는 댓글은 404 다")
        void unknownCommentIsNotFound() throws Exception {
            authenticateAs(fixture.member().getId());

            updateComment(nonExistentCommentId(), "내용")
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value(10017));
        }

        @Test
        @DisplayName("다른 방의 댓글 ID 로는 수정할 수 없다")
        void commentFromAnotherRoomIsNotFound() throws Exception {
            StudyRoom otherRoom = createRoom(fixture.member(), "남의 방");
            StudyRoomSharedProblem otherShared = saveSharedProblem(otherRoom, fixture.member(),
                    saveProblem(fixture.member().getId()), null);
            StudyRoomSharedProblemComment otherComment = saveComment(otherShared, fixture.member(), "남의 방 댓글");
            authenticateAs(fixture.member().getId());

            updateComment(otherComment.getId(), "고침")
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value(10017));

            assertThat(commentRepository.findById(otherComment.getId()).orElseThrow().getContent())
                    .as("남의 방 댓글은 그대로다").isEqualTo("남의 방 댓글");
        }
    }

    @Nested
    @DisplayName("삭제")
    class DeleteComment {

        @Test
        @DisplayName("작성자는 자기 댓글을 지울 수 있다")
        void authorCanDelete() throws Exception {
            StudyRoomSharedProblemComment comment = saveComment(sharedProblem, fixture.member(), "내 댓글");
            authenticateAs(fixture.member().getId());

            deleteComment(comment.getId()).andExpect(status().isOk());

            assertThat(commentRepository.findById(comment.getId())).as("삭제된 댓글").isEmpty();
        }

        @Test
        @DisplayName("방장은 남의 댓글도 지울 수 있다")
        void hostCanDeleteOthersComment() throws Exception {
            StudyRoomSharedProblemComment comment = saveComment(sharedProblem, fixture.member(), "멤버 댓글");
            authenticateAs(fixture.host().getId());

            deleteComment(comment.getId()).andExpect(status().isOk());

            assertThat(commentRepository.findById(comment.getId())).as("방장이 지운 댓글").isEmpty();
        }

        @Test
        @DisplayName("댓글을 지우면 그 댓글의 리액션도 함께 사라진다")
        void deletingCommentRemovesItsReactions() throws Exception {
            StudyRoomSharedProblemComment comment = saveComment(sharedProblem, fixture.member(), "내 댓글");
            commentReactionRepository.saveAndFlush(
                    StudyRoomSharedProblemCommentReaction.create(comment, fixture.host(), EMOJI));
            authenticateAs(fixture.member().getId());

            deleteComment(comment.getId()).andExpect(status().isOk());

            assertThat(commentReactionRepository.findAllByCommentId(comment.getId()))
                    .as("함께 삭제된 댓글 리액션").isEmpty();
        }

        @Test
        @DisplayName("작성자도 방장도 아닌 멤버는 지울 수 없다")
        void otherMemberCannotDelete() throws Exception {
            User third = fixtures.createUser("third");
            addMember(fixture.room(), third);
            StudyRoomSharedProblemComment comment = saveComment(sharedProblem, fixture.member(), "멤버 댓글");
            authenticateAs(third.getId());

            deleteComment(comment.getId())
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value(10019));

            assertThat(commentRepository.findById(comment.getId())).as("남아 있는 댓글").isPresent();
        }

        @Test
        @DisplayName("비멤버는 지울 수 없다")
        void nonMemberCannotDelete() throws Exception {
            StudyRoomSharedProblemComment comment = saveComment(sharedProblem, fixture.member(), "멤버 댓글");
            authenticateAs(fixture.outsider().getId());

            deleteComment(comment.getId())
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value(10002));

            assertThat(commentRepository.findById(comment.getId())).as("남아 있는 댓글").isPresent();
        }

        @Test
        @DisplayName("존재하지 않는 댓글은 404 다")
        void unknownCommentIsNotFound() throws Exception {
            authenticateAs(fixture.member().getId());

            deleteComment(nonExistentCommentId())
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value(10017));
        }
    }

    @Nested
    @DisplayName("댓글 리액션")
    class CommentReaction {

        @Test
        @DisplayName("멤버는 댓글에 리액션을 달고 취소할 수 있다")
        void toggleAddsThenRemoves() throws Exception {
            StudyRoomSharedProblemComment comment = saveComment(sharedProblem, fixture.host(), "댓글");
            authenticateAs(fixture.member().getId());

            toggleReaction(comment.getId(), EMOJI)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.commentId").value(comment.getId()))
                    .andExpect(jsonPath("$.data.reactions[0].count").value(1))
                    .andExpect(jsonPath("$.data.reactions[0].reactedByMe").value(true));

            toggleReaction(comment.getId(), EMOJI)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.reactions").isEmpty());
        }

        @Test
        @DisplayName("토글은 남의 리액션을 지우지 않는다")
        void toggleNeverDeletesOthersReaction() throws Exception {
            StudyRoomSharedProblemComment comment = saveComment(sharedProblem, fixture.host(), "댓글");
            commentReactionRepository.saveAndFlush(
                    StudyRoomSharedProblemCommentReaction.create(comment, fixture.host(), EMOJI));
            authenticateAs(fixture.member().getId());

            toggleReaction(comment.getId(), EMOJI)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.reactions[0].count").value(2));
            toggleReaction(comment.getId(), EMOJI)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.reactions[0].count").value(1));

            assertThat(commentReactionRepository.findAllByCommentId(comment.getId()))
                    .as("방장의 리액션은 남아 있다")
                    .singleElement()
                    .satisfies(reaction -> assertThat(reaction.getUser().getId()).isEqualTo(fixture.host().getId()));
        }

        @Test
        @DisplayName("허용되지 않은 이모지는 400 이다")
        void disallowedEmojiIsRejected() throws Exception {
            StudyRoomSharedProblemComment comment = saveComment(sharedProblem, fixture.host(), "댓글");
            authenticateAs(fixture.member().getId());

            toggleReaction(comment.getId(), "not_allowed")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value(11001));
        }

        @Test
        @DisplayName("비멤버는 댓글에 리액션할 수 없다")
        void nonMemberCannotReact() throws Exception {
            StudyRoomSharedProblemComment comment = saveComment(sharedProblem, fixture.host(), "댓글");
            authenticateAs(fixture.outsider().getId());

            toggleReaction(comment.getId(), EMOJI)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value(10002));

            assertThat(commentReactionRepository.findAllByCommentId(comment.getId()))
                    .as("비멤버 리액션은 저장되지 않는다").isEmpty();
        }

        @Test
        @DisplayName("존재하지 않는 댓글에는 리액션할 수 없다")
        void unknownCommentIsNotFound() throws Exception {
            authenticateAs(fixture.member().getId());

            toggleReaction(nonExistentCommentId(), EMOJI)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value(10017));
        }
    }

    private ResultActions createComment(String content) throws Exception {
        return mockMvc.perform(post("/api/study-room/{roomId}/shared-problems/{sharedProblemId}/comments",
                        fixture.roomId(), sharedProblem.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new SharedProblemCommentRequest(content))));
    }

    private ResultActions updateComment(Long commentId, String content) throws Exception {
        return mockMvc.perform(patch("/api/study-room/{roomId}/shared-problems/{sharedProblemId}/comments/{commentId}",
                        fixture.roomId(), sharedProblem.getId(), commentId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new SharedProblemCommentRequest(content))));
    }

    private ResultActions deleteComment(Long commentId) throws Exception {
        return mockMvc.perform(delete("/api/study-room/{roomId}/shared-problems/{sharedProblemId}/comments/{commentId}",
                fixture.roomId(), sharedProblem.getId(), commentId));
    }

    private ResultActions toggleReaction(Long commentId, String emoji) throws Exception {
        return mockMvc.perform(post("/api/study-room/{roomId}/shared-problems/{sharedProblemId}/comments/{commentId}/reactions",
                        fixture.roomId(), sharedProblem.getId(), commentId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ReactionToggleRequest(emoji))));
    }
}
