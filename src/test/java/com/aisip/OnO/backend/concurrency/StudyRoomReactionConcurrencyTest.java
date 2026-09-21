package com.aisip.OnO.backend.concurrency;

import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.studyroom.dto.StudyRoomDtos.ReactionToggleRequest;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomFeed;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomSharedProblem;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomSharedProblemComment;
import com.aisip.OnO.backend.studyroom.service.StudyRoomFeedService;
import com.aisip.OnO.backend.studyroom.service.StudyRoomSharedProblemCommentService;
import com.aisip.OnO.backend.studyroom.service.StudyRoomSharedProblemService;
import com.aisip.OnO.backend.studyroom.support.StudyRoomTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 리액션 토글의 동시 요청.
 *
 * <p>세 종류의 리액션(피드·공유 문제·댓글)이 모두 "이미 눌렀는지 찾아보고, 없으면 넣고 있으면 지운다"는
 * check-then-act 로 구현돼 있고, 테이블에는 {@code (대상, 사용자, 이모지)} 유니크 제약이 걸려 있다.
 * 이모지를 연타하면 같은 요청이 겹쳐 들어오는데, 두 요청이 모두 "없음"을 읽으면 두 번째 INSERT 가
 * 유니크 제약에 걸려 {@code DataIntegrityViolationException} 이 그대로 500 으로 나간다.
 * 태그 중복 생성 장애({@code ProductionIncidentRegressionTest})와 같은 구조다.
 *
 * <p>토글이라 8번 눌렀을 때 최종적으로 눌린 상태인지 아닌지는 정하기 어렵지만,
 * <b>500 이 나가지 않는다</b>는 것과 <b>같은 조합의 리액션이 두 개 이상 남지 않는다</b>는 것은
 * 어떤 순서로 처리되든 반드시 지켜져야 한다.
 */
@DisplayName("동시성 - 리액션 토글")
class StudyRoomReactionConcurrencyTest extends StudyRoomTestSupport {

    private static final int THREAD_COUNT = 8;

    @Autowired
    private StudyRoomFeedService feedService;

    @Autowired
    private StudyRoomSharedProblemService sharedProblemService;

    @Autowired
    private StudyRoomSharedProblemCommentService commentService;

    @Nested
    @DisplayName("피드 리액션")
    class FeedReaction {

        @Test
        @DisplayName("같은 이모지를 8번 연타해도 500 이 나지 않고 리액션은 최대 한 개만 남는다")
        void neverDuplicatesOrFailsUnderConcurrentToggles() {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            StudyRoomFeed feed = saveFeed(fixture.room(), fixture.host());

            ConcurrentRunner.Outcome outcome = ConcurrentRunner.runConcurrently(THREAD_COUNT,
                    () -> feedService.toggleReaction(fixture.roomId(), feed.getId(), fixture.member().getId(),
                            new ReactionToggleRequest(EMOJI)));

            assertThat(outcome.serverErrors())
                    .as("유니크 제약 위반이 그대로 올라오면 사용자에게 500 이 나간다")
                    .isEmpty();
            assertThat(countMyFeedReactions(feed, fixture.member().getId()))
                    .as("토글이 사용자 단위로 직렬화되면 짝수 번 누른 결과는 누르지 않은 상태다")
                    .isZero();
        }

        @Test
        @DisplayName("홀수 번 연타하면 리액션이 하나 남는다")
        void oddNumberOfTogglesLeavesOneReaction() {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            StudyRoomFeed feed = saveFeed(fixture.room(), fixture.host());

            ConcurrentRunner.Outcome outcome = ConcurrentRunner.runConcurrently(THREAD_COUNT - 1,
                    () -> feedService.toggleReaction(fixture.roomId(), feed.getId(), fixture.member().getId(),
                            new ReactionToggleRequest(EMOJI)));

            assertThat(outcome.serverErrors()).isEmpty();
            assertThat(countMyFeedReactions(feed, fixture.member().getId()))
                    .as("홀수 번 토글의 최종 상태")
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("서로 다른 멤버가 같은 피드에 동시에 반응하면 전원의 리액션이 남는다")
        void keepsEveryMembersReaction() {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            StudyRoomFeed feed = saveFeed(fixture.room(), fixture.host());
            var reactors = new java.util.ArrayList<Long>();
            for (int i = 0; i < THREAD_COUNT; i++) {
                var user = fixtures.createUser("reactor");
                addMember(fixture.room(), user);
                reactors.add(user.getId());
            }

            ConcurrentRunner.Outcome outcome = ConcurrentRunner.runConcurrently(THREAD_COUNT,
                    index -> feedService.toggleReaction(fixture.roomId(), feed.getId(), reactors.get(index),
                            new ReactionToggleRequest(EMOJI)));

            assertThat(outcome.failures()).as("사용자가 다르면 충돌할 이유가 없다").isEmpty();
            assertThat(feedReactionRepository.findAllByFeedId(feed.getId()))
                    .as("여덟 명의 리액션이 모두 남아야 한다")
                    .hasSize(THREAD_COUNT);
        }
    }

    @Nested
    @DisplayName("공유 문제 리액션")
    class SharedProblemReaction {

        @Test
        @DisplayName("같은 이모지를 8번 연타해도 500 이 나지 않고 리액션은 최대 한 개만 남는다")
        void neverDuplicatesOrFailsUnderConcurrentToggles() {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            Problem problem = saveProblem(fixture.host().getId());
            StudyRoomSharedProblem shared = saveSharedProblem(fixture.room(), fixture.host(), problem, "같이 봐요");

            ConcurrentRunner.Outcome outcome = ConcurrentRunner.runConcurrently(THREAD_COUNT,
                    () -> sharedProblemService.toggleReaction(fixture.roomId(), shared.getId(),
                            fixture.member().getId(), new ReactionToggleRequest(EMOJI)));

            assertThat(outcome.serverErrors()).isEmpty();
            assertThat(sharedProblemReactionRepository.findAllBySharedProblemId(shared.getId()).stream()
                    .filter(reaction -> reaction.getUser().getId().equals(fixture.member().getId()))
                    .filter(reaction -> reaction.getEmoji().equals(EMOJI))
                    .count())
                    .as("같은 (공유 문제, 사용자, 이모지) 조합은 하나를 넘을 수 없다")
                    .isLessThanOrEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("댓글 리액션")
    class CommentReaction {

        @Test
        @DisplayName("같은 이모지를 8번 연타해도 500 이 나지 않고 리액션은 최대 한 개만 남는다")
        void neverDuplicatesOrFailsUnderConcurrentToggles() {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            Problem problem = saveProblem(fixture.host().getId());
            StudyRoomSharedProblem shared = saveSharedProblem(fixture.room(), fixture.host(), problem, "같이 봐요");
            StudyRoomSharedProblemComment comment = saveComment(shared, fixture.host(), "이거 어렵네요");

            ConcurrentRunner.Outcome outcome = ConcurrentRunner.runConcurrently(THREAD_COUNT,
                    () -> commentService.toggleReaction(fixture.roomId(), shared.getId(), comment.getId(),
                            fixture.member().getId(), new ReactionToggleRequest(EMOJI)));

            assertThat(outcome.serverErrors()).isEmpty();
            assertThat(commentReactionRepository.findAllByCommentId(comment.getId()).stream()
                    .filter(reaction -> reaction.getUser().getId().equals(fixture.member().getId()))
                    .count())
                    .as("같은 (댓글, 사용자, 이모지) 조합은 하나를 넘을 수 없다")
                    .isLessThanOrEqualTo(1L);
        }
    }

    private long countMyFeedReactions(StudyRoomFeed feed, Long userId) {
        return feedReactionRepository.findAllByFeedId(feed.getId()).stream()
                .filter(reaction -> reaction.getUser().getId().equals(userId))
                .filter(reaction -> reaction.getEmoji().equals(EMOJI))
                .count();
    }
}
