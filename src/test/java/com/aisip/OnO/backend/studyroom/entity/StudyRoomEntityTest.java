package com.aisip.OnO.backend.studyroom.entity;

import com.aisip.OnO.backend.problem.dto.ProblemRegisterDto;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.support.TestUsers;
import com.aisip.OnO.backend.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 스터디룸 엔티티의 팩터리와 상태 전이에 대한 순수 단위 테스트.
 *
 * <p>스프링 컨텍스트도 DB도 필요 없다. 엔티티는 커버리지가 가장 낮은 층이면서
 * 방장 위임·초대 코드 만료·챌린지 상태 같은 핵심 불변식이 실제로 구현된 곳이라,
 * 서비스 테스트를 통과시키는 부수효과가 아니라 직접 검증한다.
 */
@DisplayName("스터디룸 엔티티")
class StudyRoomEntityTest {

    private static User user(String name) {
        return TestUsers.create("GOOGLE", name, "entity");
    }

    @Nested
    @DisplayName("StudyRoom")
    class StudyRoomTest {

        @Test
        @DisplayName("생성 직후에는 멤버가 비어 있고 방장 ID가 그대로 담긴다")
        void createStartsWithHostAndNoMember() {
            StudyRoom room = StudyRoom.create("스터디룸", 42L);

            assertThat(room.getName()).as("이름").isEqualTo("스터디룸");
            assertThat(room.getHostUserId()).as("방장 ID").isEqualTo(42L);
            assertThat(room.getMembers()).as("초기 멤버 목록").isEmpty();
            assertThat(room.getThumbnailUrl()).as("초기 썸네일").isNull();
        }

        @Test
        @DisplayName("addMember 는 양방향 연관관계를 함께 세운다")
        void addMemberSetsBothSidesOfAssociation() {
            StudyRoom room = StudyRoom.create("스터디룸", 1L);
            StudyRoomMember member = StudyRoomMember.create(user("멤버"), StudyRoomMemberRole.MEMBER);

            room.addMember(member);

            assertThat(room.getMembers()).as("방의 멤버 목록").containsExactly(member);
            assertThat(member.getRoom()).as("멤버가 보는 방").isSameAs(room);
        }

        @Test
        @DisplayName("여러 멤버를 추가하면 추가한 순서대로 쌓인다")
        void addMemberAccumulatesInOrder() {
            StudyRoom room = StudyRoom.create("스터디룸", 1L);
            StudyRoomMember first = StudyRoomMember.create(user("첫째"), StudyRoomMemberRole.HOST);
            StudyRoomMember second = StudyRoomMember.create(user("둘째"), StudyRoomMemberRole.MEMBER);

            room.addMember(first);
            room.addMember(second);

            assertThat(room.getMembers()).as("멤버 추가 순서").containsExactly(first, second);
        }

        @Test
        @DisplayName("방장 위임은 hostUserId 를 새 방장으로 바꾼다")
        void updateHostUserIdReplacesHost() {
            StudyRoom room = StudyRoom.create("스터디룸", 1L);

            room.updateHostUserId(2L);

            assertThat(room.getHostUserId()).as("위임 후 방장 ID").isEqualTo(2L);
        }

        @Test
        @DisplayName("이름과 썸네일은 각각 독립적으로 갱신된다")
        void updateNameAndThumbnailAreIndependent() {
            StudyRoom room = StudyRoom.create("이전 이름", 1L);

            room.updateName("새 이름");

            assertThat(room.getName()).as("이름 변경 결과").isEqualTo("새 이름");
            assertThat(room.getThumbnailUrl()).as("이름만 바꿨을 때 썸네일").isNull();

            room.updateThumbnailUrl("https://cdn.example.com/a.png");

            assertThat(room.getThumbnailUrl()).as("썸네일 변경 결과").isEqualTo("https://cdn.example.com/a.png");
            assertThat(room.getName()).as("썸네일만 바꿨을 때 이름").isEqualTo("새 이름");
        }

        @Test
        @DisplayName("썸네일을 null 로 되돌려 제거할 수 있다")
        void thumbnailCanBeClearedToNull() {
            StudyRoom room = StudyRoom.create("스터디룸", 1L);
            room.updateThumbnailUrl("https://cdn.example.com/a.png");

            room.updateThumbnailUrl(null);

            assertThat(room.getThumbnailUrl()).as("제거된 썸네일").isNull();
        }
    }

    @Nested
    @DisplayName("StudyRoomMember")
    class StudyRoomMemberTest {

        @Test
        @DisplayName("생성 시 역할이 지정되고 주간 목표는 비어 있다")
        void createKeepsRoleAndLeavesGoalEmpty() {
            StudyRoomMember member = StudyRoomMember.create(user("멤버"), StudyRoomMemberRole.MEMBER);

            assertThat(member.getRole()).as("역할").isEqualTo(StudyRoomMemberRole.MEMBER);
            assertThat(member.getWeeklyGoal()).as("초기 주간 목표").isNull();
            assertThat(member.getRoom()).as("초기 방").isNull();
        }

        @ParameterizedTest(name = "역할 {0}")
        @EnumSource(StudyRoomMemberRole.class)
        @DisplayName("어떤 역할로도 생성할 수 있다")
        void createAcceptsEveryRole(StudyRoomMemberRole role) {
            StudyRoomMember member = StudyRoomMember.create(user("멤버"), role);

            assertThat(member.getRole()).as("생성된 역할").isEqualTo(role);
        }

        @Test
        @DisplayName("promoteToHost 는 일반 멤버를 방장으로 승격한다")
        void promoteToHostChangesRole() {
            StudyRoomMember member = StudyRoomMember.create(user("멤버"), StudyRoomMemberRole.MEMBER);

            member.promoteToHost();

            assertThat(member.getRole()).as("승격 후 역할").isEqualTo(StudyRoomMemberRole.HOST);
        }

        @Test
        @DisplayName("이미 방장인 멤버를 승격해도 방장 그대로다")
        void promoteToHostIsIdempotent() {
            StudyRoomMember member = StudyRoomMember.create(user("방장"), StudyRoomMemberRole.HOST);

            member.promoteToHost();

            assertThat(member.getRole()).as("재승격 후 역할").isEqualTo(StudyRoomMemberRole.HOST);
        }

        @Test
        @DisplayName("주간 목표는 설정과 해제를 반복할 수 있다")
        void weeklyGoalCanBeSetAndCleared() {
            StudyRoomMember member = StudyRoomMember.create(user("멤버"), StudyRoomMemberRole.MEMBER);

            member.updateWeeklyGoal(7);
            assertThat(member.getWeeklyGoal()).as("설정된 주간 목표").isEqualTo(7);

            member.updateWeeklyGoal(null);
            assertThat(member.getWeeklyGoal()).as("해제된 주간 목표").isNull();
        }
    }

    @Nested
    @DisplayName("StudyRoomInviteCode")
    class StudyRoomInviteCodeTest {

        private final StudyRoom room = StudyRoom.create("스터디룸", 1L);

        @Test
        @DisplayName("만료 시각이 현재보다 뒤면 유효하다")
        void notExpiredBeforeExpiry() {
            LocalDateTime now = LocalDateTime.of(2026, 1, 1, 12, 0);
            StudyRoomInviteCode code = StudyRoomInviteCode.create(room, "123456", now.plusHours(24));

            assertThat(code.isExpired(now)).as("발급 직후 만료 여부").isFalse();
            assertThat(code.isExpired(now.plusHours(23).plusMinutes(59))).as("만료 1분 전").isFalse();
        }

        @Test
        @DisplayName("만료 시각과 정확히 같은 순간은 이미 만료로 본다")
        void expiryBoundaryIsInclusive() {
            LocalDateTime expiredAt = LocalDateTime.of(2026, 1, 2, 12, 0);
            StudyRoomInviteCode code = StudyRoomInviteCode.create(room, "123456", expiredAt);

            assertThat(code.isExpired(expiredAt))
                    .as("만료 시각과 동일한 순간 — expiredAt 이 now 보다 뒤가 아니므로 만료")
                    .isTrue();
        }

        @Test
        @DisplayName("만료 시각을 지나면 만료다")
        void expiredAfterExpiry() {
            LocalDateTime expiredAt = LocalDateTime.of(2026, 1, 2, 12, 0);
            StudyRoomInviteCode code = StudyRoomInviteCode.create(room, "123456", expiredAt);

            assertThat(code.isExpired(expiredAt.plusNanos(1))).as("만료 직후").isTrue();
            assertThat(code.isExpired(expiredAt.plusDays(1))).as("하루 뒤").isTrue();
        }

        @Test
        @DisplayName("코드 문자열과 방은 그대로 보관된다")
        void createKeepsCodeAndRoom() {
            StudyRoomInviteCode code = StudyRoomInviteCode.create(room, "000001", LocalDateTime.now());

            assertThat(code.getCode()).as("초대 코드").isEqualTo("000001");
            assertThat(code.getRoom()).as("코드가 가리키는 방").isSameAs(room);
        }
    }

    @Nested
    @DisplayName("StudyRoomChallenge")
    class StudyRoomChallengeTest {

        private final StudyRoom room = StudyRoom.create("스터디룸", 1L);

        @Test
        @DisplayName("생성 직후 상태는 항상 IN_PROGRESS 이고 완료 시각은 비어 있다")
        void createStartsInProgress() {
            LocalDateTime startAt = LocalDateTime.now();
            StudyRoomChallenge challenge = StudyRoomChallenge.create(room, "챌린지",
                    StudyRoomChallengeType.INDIVIDUAL, StudyRoomChallengeMetric.PROBLEM_COUNT,
                    null, null, 5, startAt, startAt.plusDays(7));

            assertThat(challenge.getStatus()).as("초기 상태").isEqualTo(StudyRoomChallengeStatus.IN_PROGRESS);
            assertThat(challenge.getCompletedAt()).as("초기 완료 시각").isNull();
            assertThat(challenge.getTargetValue()).as("목표치").isEqualTo(5);
        }

        @ParameterizedTest(name = "{0} 로 전이")
        @EnumSource(StudyRoomChallengeStatus.class)
        @DisplayName("어떤 상태로도 전이할 수 있다")
        void updateStatusAcceptsEveryStatus(StudyRoomChallengeStatus status) {
            StudyRoomChallenge challenge = inProgressChallenge();

            challenge.updateStatus(status);

            assertThat(challenge.getStatus()).as("전이된 상태").isEqualTo(status);
        }

        @Test
        @DisplayName("IN_PROGRESS → COMPLETED → EXPIRED 순으로 연달아 전이해도 마지막 값이 남는다")
        void statusTransitionsAreSequential() {
            StudyRoomChallenge challenge = inProgressChallenge();

            challenge.updateStatus(StudyRoomChallengeStatus.COMPLETED);
            challenge.updateStatus(StudyRoomChallengeStatus.EXPIRED);

            assertThat(challenge.getStatus()).as("마지막 상태").isEqualTo(StudyRoomChallengeStatus.EXPIRED);
        }

        @Test
        @DisplayName("period 를 쓰면 periodDays 없이, periodDays 를 쓰면 period 없이 저장된다")
        void periodAndPeriodDaysAreMutuallyExclusiveByConstruction() {
            LocalDateTime startAt = LocalDateTime.now();
            StudyRoomChallenge weekly = StudyRoomChallenge.create(room, "주간", StudyRoomChallengeType.GROUP,
                    StudyRoomChallengeMetric.PRACTICE_COUNT, StudyRoomChallengePeriod.WEEKLY, null, 3,
                    startAt, startAt.plusDays(21));
            StudyRoomChallenge custom = StudyRoomChallenge.create(room, "3일 주기", StudyRoomChallengeType.GROUP,
                    StudyRoomChallengeMetric.PRACTICE_COUNT, null, 3, 3,
                    startAt, startAt.plusDays(21));

            assertThat(weekly.getPeriod()).as("주기 챌린지의 period").isEqualTo(StudyRoomChallengePeriod.WEEKLY);
            assertThat(weekly.getPeriodDays()).as("주기 챌린지의 periodDays").isNull();
            assertThat(custom.getPeriod()).as("커스텀 주기 챌린지의 period").isNull();
            assertThat(custom.getPeriodDays()).as("커스텀 주기 챌린지의 periodDays").isEqualTo(3);
        }

        @Test
        @DisplayName("기간이 0일인 챌린지(시작=종료)도 엔티티 수준에서는 만들어진다")
        void zeroLengthPeriodIsAcceptedByEntity() {
            LocalDateTime at = LocalDateTime.now();
            StudyRoomChallenge challenge = StudyRoomChallenge.create(room, "0일", StudyRoomChallengeType.INDIVIDUAL,
                    StudyRoomChallengeMetric.PROBLEM_COUNT, null, null, 1, at, at);

            assertThat(challenge.getStartAt())
                    .as("시작과 종료가 같은 챌린지 — 검증은 서비스 계층 책임이다")
                    .isEqualTo(challenge.getEndAt());
        }

        private StudyRoomChallenge inProgressChallenge() {
            LocalDateTime startAt = LocalDateTime.now();
            return StudyRoomChallenge.create(room, "챌린지", StudyRoomChallengeType.INDIVIDUAL,
                    StudyRoomChallengeMetric.PROBLEM_COUNT, null, null, 5, startAt, startAt.plusDays(7));
        }
    }

    @Nested
    @DisplayName("StudyRoomFeed")
    class StudyRoomFeedTest {

        private final StudyRoom room = StudyRoom.create("스터디룸", 1L);

        @ParameterizedTest(name = "{0}")
        @EnumSource(StudyRoomFeedEventType.class)
        @DisplayName("모든 이벤트 종류로 피드를 만들 수 있다")
        void createAcceptsEveryEventType(StudyRoomFeedEventType eventType) {
            StudyRoomFeed feed = StudyRoomFeed.create(room, user("작성자"), eventType, "{}");

            assertThat(feed.getEventType()).as("피드 이벤트 종류").isEqualTo(eventType);
            assertThat(feed.getRoom()).as("피드가 속한 방").isSameAs(room);
        }

        @Test
        @DisplayName("metadataJson 은 갱신할 수 있고 null 도 허용한다")
        void metadataJsonCanBeUpdatedAndNulled() {
            StudyRoomFeed feed = StudyRoomFeed.create(room, user("작성자"),
                    StudyRoomFeedEventType.PROBLEM_REGISTERED, "{\"count\":1}");

            feed.updateMetadataJson("{\"count\":3}");
            assertThat(feed.getMetadataJson()).as("누적된 metadata").isEqualTo("{\"count\":3}");

            feed.updateMetadataJson(null);
            assertThat(feed.getMetadataJson()).as("비운 metadata").isNull();
        }

        @ParameterizedTest(name = "깨진 값: {0}")
        @ValueSource(strings = {"", "   ", "not-json", "{", "[1,2,3]", "{\"count\":"})
        @DisplayName("깨진 metadata_json 도 엔티티는 그대로 보관한다 — 방어는 읽는 쪽 책임이다")
        void brokenMetadataIsStoredAsIs(String broken) {
            StudyRoomFeed feed = StudyRoomFeed.create(room, user("작성자"),
                    StudyRoomFeedEventType.PROBLEM_REGISTERED, broken);

            assertThat(feed.getMetadataJson()).as("보관된 원본 문자열").isEqualTo(broken);
        }
    }

    @Nested
    @DisplayName("리액션 엔티티")
    class ReactionEntityTest {

        private final StudyRoom room = StudyRoom.create("스터디룸", 1L);

        @Test
        @DisplayName("피드 리액션은 피드·사용자·이모지를 그대로 보관한다")
        void feedReactionKeepsItsTriple() {
            User reactor = user("리액터");
            StudyRoomFeed feed = StudyRoomFeed.create(room, user("작성자"),
                    StudyRoomFeedEventType.PROBLEM_REGISTERED, "{}");

            StudyRoomFeedReaction reaction = StudyRoomFeedReaction.create(feed, reactor, "gold_medal");

            assertThat(reaction.getFeed()).as("리액션 대상 피드").isSameAs(feed);
            assertThat(reaction.getUser()).as("리액션한 사용자").isSameAs(reactor);
            assertThat(reaction.getEmoji()).as("이모지 키").isEqualTo("gold_medal");
        }

        @Test
        @DisplayName("공유 문제 리액션과 댓글 리액션도 같은 방식으로 만들어진다")
        void sharedProblemAndCommentReactionsKeepTheirTriple() {
            User author = user("작성자");
            User reactor = user("리액터");
            Problem problem = problem(author.getId());
            StudyRoomSharedProblem sharedProblem = StudyRoomSharedProblem.create(room, author, problem, "코멘트");
            StudyRoomSharedProblemComment comment = StudyRoomSharedProblemComment.create(sharedProblem, author, "댓글");

            StudyRoomSharedProblemReaction sharedReaction =
                    StudyRoomSharedProblemReaction.create(sharedProblem, reactor, "gold_medal");
            StudyRoomSharedProblemCommentReaction commentReaction =
                    StudyRoomSharedProblemCommentReaction.create(comment, reactor, "thumbs_up_happy");

            assertThat(sharedReaction.getSharedProblem()).as("공유 문제 리액션 대상").isSameAs(sharedProblem);
            assertThat(sharedReaction.getEmoji()).as("공유 문제 리액션 이모지").isEqualTo("gold_medal");
            assertThat(commentReaction.getComment()).as("댓글 리액션 대상").isSameAs(comment);
            assertThat(commentReaction.getEmoji()).as("댓글 리액션 이모지").isEqualTo("thumbs_up_happy");
        }
    }

    @Nested
    @DisplayName("StudyRoomSharedProblem 과 댓글")
    class SharedProblemEntityTest {

        private final StudyRoom room = StudyRoom.create("스터디룸", 1L);

        @Test
        @DisplayName("공유 문제는 공유자·문제·코멘트를 보관하고 코멘트는 비어 있어도 된다")
        void sharedProblemAllowsNullComment() {
            User sharer = user("공유자");
            Problem problem = problem(sharer.getId());

            StudyRoomSharedProblem withComment = StudyRoomSharedProblem.create(room, sharer, problem, "같이 봐요");
            StudyRoomSharedProblem withoutComment = StudyRoomSharedProblem.create(room, sharer, problem, null);

            assertThat(withComment.getComment()).as("코멘트 있는 공유").isEqualTo("같이 봐요");
            assertThat(withComment.getSharedByUser()).as("공유자").isSameAs(sharer);
            assertThat(withoutComment.getComment()).as("코멘트 없는 공유").isNull();
        }

        @Test
        @DisplayName("댓글 내용은 수정할 수 있다")
        void commentContentCanBeUpdated() {
            User author = user("작성자");
            StudyRoomSharedProblem sharedProblem =
                    StudyRoomSharedProblem.create(room, author, problem(author.getId()), null);
            StudyRoomSharedProblemComment comment =
                    StudyRoomSharedProblemComment.create(sharedProblem, author, "처음 내용");

            comment.updateContent("수정한 내용");

            assertThat(comment.getContent()).as("수정된 댓글 내용").isEqualTo("수정한 내용");
            assertThat(comment.getSharedProblem()).as("댓글이 달린 공유 문제").isSameAs(sharedProblem);
        }
    }

    @Nested
    @DisplayName("StudyRoomWeeklyReport 와 읽음 기록")
    class WeeklyReportEntityTest {

        private final StudyRoom room = StudyRoom.create("스터디룸", 1L);

        @Test
        @DisplayName("활동이 전혀 없는 주의 리포트는 0 기반으로 만들어지고 이름·프로필이 비어도 된다")
        void emptyWeekReportIsZeroBased() {
            LocalDate weekStart = LocalDate.of(2026, 1, 5);

            StudyRoomWeeklyReport report = StudyRoomWeeklyReport.create(room, weekStart, weekStart.plusDays(6),
                    null, null, 0, null, null, 0, 0, 0, "이번 주도 모두 고생했어요!");

            assertThat(report.getTopMemberName()).as("탑 멤버 이름").isNull();
            assertThat(report.getTopMemberProfileImageUrl()).as("탑 멤버 프로필 이미지").isNull();
            assertThat(report.getLongestStreakName()).as("최장 스트릭 이름").isNull();
            assertThat(report.getLongestStreakProfileImageUrl()).as("최장 스트릭 프로필 이미지").isNull();
            assertThat(report.getTopMemberProblemCount()).as("탑 멤버 문제 수").isZero();
            assertThat(report.getLongestStreakDays()).as("최장 스트릭 일수").isZero();
            assertThat(report.getTotalProblems()).as("총 문제 수").isZero();
            assertThat(report.getChallengesCompleted()).as("완료 챌린지 수").isZero();
        }

        @Test
        @DisplayName("주 시작일과 종료일은 6일 간격으로 그대로 보관된다")
        void weekRangeIsKeptAsGiven() {
            LocalDate weekStart = LocalDate.of(2026, 1, 5);

            StudyRoomWeeklyReport report = StudyRoomWeeklyReport.create(room, weekStart, weekStart.plusDays(6),
                    "탑", "https://cdn.example.com/top.png", 12,
                    "스트릭", "https://cdn.example.com/streak.png", 7,
                    30, 2, "잘했어요");

            assertThat(report.getWeekStart()).as("주 시작일").isEqualTo(weekStart);
            assertThat(report.getWeekEnd()).as("주 종료일").isEqualTo(LocalDate.of(2026, 1, 11));
            assertThat(report.getCheerMessage()).as("응원 메시지").isEqualTo("잘했어요");
        }

        @Test
        @DisplayName("읽음 기록은 리포트·사용자·읽은 시각을 보관한다")
        void readRecordKeepsReportUserAndTime() {
            LocalDate weekStart = LocalDate.of(2026, 1, 5);
            StudyRoomWeeklyReport report = StudyRoomWeeklyReport.create(room, weekStart, weekStart.plusDays(6),
                    null, null, 0, null, null, 0, 0, 0, "메시지");
            User reader = user("독자");
            LocalDateTime readAt = LocalDateTime.of(2026, 1, 12, 9, 0);

            StudyRoomWeeklyReportRead read = StudyRoomWeeklyReportRead.create(report, reader, readAt);

            assertThat(read.getReport()).as("읽은 리포트").isSameAs(report);
            assertThat(read.getUser()).as("읽은 사용자").isSameAs(reader);
            assertThat(read.getReadAt()).as("읽은 시각").isEqualTo(readAt);
        }
    }

    private static Problem problem(Long userId) {
        return Problem.from(new ProblemRegisterDto(null, "메모", "출처", null, LocalDateTime.now()), userId);
    }
}
