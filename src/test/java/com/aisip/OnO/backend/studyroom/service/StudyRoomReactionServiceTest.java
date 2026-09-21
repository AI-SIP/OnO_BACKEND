package com.aisip.OnO.backend.studyroom.service;

import com.aisip.OnO.backend.problem.dto.ProblemRegisterDto;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.studyroom.dto.StudyRoomDtos.ReactionResponse;
import com.aisip.OnO.backend.studyroom.entity.*;
import com.aisip.OnO.backend.support.TestUsers;
import com.aisip.OnO.backend.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.util.ReflectionTestUtils.setField;

/**
 * 리액션 집계 로직의 순수 단위 테스트.
 *
 * <p>{@code reactedByMe} 를 잘못 계산하면 남의 리액션이 내 것처럼 보이거나 그 반대가 된다.
 * 세 종류(피드/공유 문제/댓글)가 같은 제네릭 구현을 공유하므로 셋 다 같은 규칙을 따르는지 확인한다.
 */
@DisplayName("StudyRoomReactionService")
class StudyRoomReactionServiceTest {

    private static final AtomicLong SEQUENCE = new AtomicLong();

    private final StudyRoomReactionService reactionService = new StudyRoomReactionService();
    private final StudyRoom room = StudyRoom.create("스터디룸", 1L);

    @Nested
    @DisplayName("피드 리액션 집계")
    class FeedReactions {

        @Test
        @DisplayName("리액션이 없으면 빈 목록이다")
        void emptyInput() {
            assertThat(reactionService.summarizeFeedReactions(List.of(), 1L))
                    .as("빈 입력의 집계 결과").isEmpty();
        }

        @Test
        @DisplayName("같은 이모지는 하나로 묶여 count 가 합산된다")
        void sameEmojiIsGrouped() {
            User me = user();
            User other = user();
            StudyRoomFeed feed = feed(me);

            List<ReactionResponse> result = reactionService.summarizeFeedReactions(List.of(
                    StudyRoomFeedReaction.create(feed, me, "gold_medal"),
                    StudyRoomFeedReaction.create(feed, other, "gold_medal")
            ), me.getId());

            assertThat(result).singleElement().satisfies(reaction -> {
                assertThat(reaction.emoji()).as("이모지").isEqualTo("gold_medal");
                assertThat(reaction.count()).as("합산된 개수").isEqualTo(2);
                assertThat(reaction.reactedByMe()).as("내가 눌렀는지").isTrue();
            });
        }

        @Test
        @DisplayName("내가 누르지 않은 이모지는 reactedByMe 가 false 다")
        void reactedByMeIsFalseForOthersOnly() {
            User me = user();
            User other = user();
            StudyRoomFeed feed = feed(me);

            List<ReactionResponse> result = reactionService.summarizeFeedReactions(List.of(
                    StudyRoomFeedReaction.create(feed, other, "gold_medal")
            ), me.getId());

            assertThat(result).singleElement().satisfies(reaction -> {
                assertThat(reaction.count()).as("개수").isEqualTo(1);
                assertThat(reaction.reactedByMe()).as("내가 눌렀는지").isFalse();
            });
        }

        @Test
        @DisplayName("서로 다른 이모지는 처음 등장한 순서대로 나열된다")
        void differentEmojisKeepInsertionOrder() {
            User me = user();
            StudyRoomFeed feed = feed(me);

            List<ReactionResponse> result = reactionService.summarizeFeedReactions(List.of(
                    StudyRoomFeedReaction.create(feed, me, "gold_medal"),
                    StudyRoomFeedReaction.create(feed, me, "thumbs_up_happy"),
                    StudyRoomFeedReaction.create(feed, me, "gold_medal")
            ), me.getId());

            assertThat(result)
                    .as("이모지 순서")
                    .extracting(ReactionResponse::emoji)
                    .containsExactly("gold_medal", "thumbs_up_happy");
            assertThat(result.get(0).count()).as("첫 이모지 개수").isEqualTo(2);
            assertThat(result.get(1).count()).as("두 번째 이모지 개수").isEqualTo(1);
        }

        @Test
        @DisplayName("여러 이모지 중 내가 누른 것만 reactedByMe 가 true 다")
        void reactedByMeIsPerEmoji() {
            User me = user();
            User other = user();
            StudyRoomFeed feed = feed(me);

            List<ReactionResponse> result = reactionService.summarizeFeedReactions(List.of(
                    StudyRoomFeedReaction.create(feed, me, "gold_medal"),
                    StudyRoomFeedReaction.create(feed, other, "thumbs_up_happy")
            ), me.getId());

            assertThat(result)
                    .as("이모지별 내 반응 여부")
                    .extracting(ReactionResponse::emoji, ReactionResponse::reactedByMe)
                    .containsExactly(
                            org.assertj.core.api.Assertions.tuple("gold_medal", true),
                            org.assertj.core.api.Assertions.tuple("thumbs_up_happy", false));
        }
    }

    @Nested
    @DisplayName("공유 문제·댓글 리액션 집계")
    class SharedProblemReactions {

        @Test
        @DisplayName("공유 문제 리액션도 같은 규칙으로 집계된다")
        void sharedProblemReactionsFollowSameRule() {
            User me = user();
            User other = user();
            StudyRoomSharedProblem sharedProblem = sharedProblem(me);

            List<ReactionResponse> result = reactionService.summarizeSharedProblemReactions(List.of(
                    StudyRoomSharedProblemReaction.create(sharedProblem, me, "gold_medal"),
                    StudyRoomSharedProblemReaction.create(sharedProblem, other, "gold_medal")
            ), other.getId());

            assertThat(result).singleElement().satisfies(reaction -> {
                assertThat(reaction.count()).as("합산된 개수").isEqualTo(2);
                assertThat(reaction.reactedByMe()).as("상대 기준 내 반응 여부").isTrue();
            });
        }

        @Test
        @DisplayName("댓글 리액션도 같은 규칙으로 집계된다")
        void commentReactionsFollowSameRule() {
            User me = user();
            User other = user();
            StudyRoomSharedProblemComment comment =
                    StudyRoomSharedProblemComment.create(sharedProblem(me), me, "댓글");

            List<ReactionResponse> result = reactionService.summarizeSharedProblemCommentReactions(List.of(
                    StudyRoomSharedProblemCommentReaction.create(comment, other, "holding_heart")
            ), me.getId());

            assertThat(result).singleElement().satisfies(reaction -> {
                assertThat(reaction.emoji()).as("이모지").isEqualTo("holding_heart");
                assertThat(reaction.count()).as("개수").isEqualTo(1);
                assertThat(reaction.reactedByMe()).as("내가 눌렀는지").isFalse();
            });
        }

        @Test
        @DisplayName("공유 문제·댓글 리액션도 빈 입력에는 빈 목록을 준다")
        void emptyInputs() {
            assertThat(reactionService.summarizeSharedProblemReactions(List.of(), 1L))
                    .as("공유 문제 빈 입력").isEmpty();
            assertThat(reactionService.summarizeSharedProblemCommentReactions(List.of(), 1L))
                    .as("댓글 빈 입력").isEmpty();
        }
    }

    private User user() {
        User user = TestUsers.create("GOOGLE", "리액터", "reaction-unit");
        setField(user, "id", SEQUENCE.incrementAndGet());
        return user;
    }

    private StudyRoomFeed feed(User author) {
        return StudyRoomFeed.create(room, author, StudyRoomFeedEventType.PROBLEM_REGISTERED, "{}");
    }

    private StudyRoomSharedProblem sharedProblem(User sharer) {
        Problem problem = Problem.from(
                new ProblemRegisterDto(null, "메모", "출처", null, LocalDateTime.now()), sharer.getId());
        return StudyRoomSharedProblem.create(room, sharer, problem, null);
    }
}
