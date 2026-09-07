package com.aisip.OnO.backend.studyroom.entity;

import com.aisip.OnO.backend.studyroom.support.StudyRoomTestSupport;
import com.aisip.OnO.backend.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 스터디룸 엔티티의 DB 제약 검증.
 *
 * <p>중복 리액션·중복 멤버십·중복 초대 코드는 서비스 계층에서도 막지만, 동시 요청이 겹치면
 * 최종 방어선은 유니크 인덱스다. 인덱스가 실제로 걸려 있는지는 스키마를 만들어 봐야 알 수 있어
 * 프로덕션과 같은 MySQL 위에서 확인한다.
 *
 * <p>연관관계 정리(방 삭제 시 멤버 제거)도 여기서 함께 본다.
 */
@DisplayName("스터디룸 엔티티 영속성 제약")
class StudyRoomPersistenceConstraintTest extends StudyRoomTestSupport {

    @Nested
    @DisplayName("유니크 제약")
    class UniqueConstraints {

        @Test
        @DisplayName("같은 방에 같은 사용자를 두 번 넣을 수 없다")
        void memberIsUniquePerRoomAndUser() {
            RoomFixture fixture = createRoomWithMemberAndOutsider();

            assertThatThrownBy(() -> addMember(fixture.room(), fixture.member()))
                    .as("중복 멤버십")
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("초대 코드는 전체에서 유일하다")
        void inviteCodeIsGloballyUnique() {
            User host = fixtures.createUser("host");
            StudyRoom first = createRoom(host, "첫방");
            StudyRoom second = createRoom(host, "둘째방");
            saveValidInviteCode(first, "123456");

            assertThatThrownBy(() -> saveValidInviteCode(second, "123456"))
                    .as("다른 방이라도 같은 코드는 쓸 수 없다")
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("같은 피드에 같은 사용자가 같은 이모지를 두 번 남길 수 없다")
        void feedReactionIsUniquePerFeedUserEmoji() {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            StudyRoomFeed feed = saveFeed(fixture.room(), fixture.host());
            saveFeedReaction(feed, fixture.host(), EMOJI);

            assertThatThrownBy(() -> saveFeedReaction(feed, fixture.host(), EMOJI))
                    .as("중복 리액션")
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("같은 피드라도 이모지가 다르면 남길 수 있다")
        void differentEmojiOnSameFeedIsAllowed() {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            StudyRoomFeed feed = saveFeed(fixture.room(), fixture.host());
            saveFeedReaction(feed, fixture.host(), EMOJI);

            assertThatCode(() -> saveFeedReaction(feed, fixture.host(), OTHER_EMOJI))
                    .as("다른 이모지")
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("같은 공유 문제에 같은 사용자가 같은 이모지를 두 번 남길 수 없다")
        void sharedProblemReactionIsUnique() {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            StudyRoomSharedProblem shared = saveSharedProblem(fixture.room(), fixture.host(),
                    saveProblem(fixture.host().getId()), null);
            sharedProblemReactionRepository.saveAndFlush(
                    StudyRoomSharedProblemReaction.create(shared, fixture.host(), EMOJI));

            assertThatThrownBy(() -> sharedProblemReactionRepository.saveAndFlush(
                    StudyRoomSharedProblemReaction.create(shared, fixture.host(), EMOJI)))
                    .as("중복 공유 문제 리액션")
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("같은 댓글에 같은 사용자가 같은 이모지를 두 번 남길 수 없다")
        void commentReactionIsUnique() {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            StudyRoomSharedProblem shared = saveSharedProblem(fixture.room(), fixture.host(),
                    saveProblem(fixture.host().getId()), null);
            StudyRoomSharedProblemComment comment = saveComment(shared, fixture.host(), "댓글");
            commentReactionRepository.saveAndFlush(
                    StudyRoomSharedProblemCommentReaction.create(comment, fixture.member(), EMOJI));

            assertThatThrownBy(() -> commentReactionRepository.saveAndFlush(
                    StudyRoomSharedProblemCommentReaction.create(comment, fixture.member(), EMOJI)))
                    .as("중복 댓글 리액션")
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("한 방에 같은 주의 리포트는 하나만 있을 수 있다")
        void weeklyReportIsUniquePerRoomAndWeek() {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            LocalDate weekStart = LocalDate.of(2026, 1, 5);
            saveWeeklyReport(fixture.room(), weekStart);

            assertThatThrownBy(() -> saveWeeklyReport(fixture.room(), weekStart))
                    .as("같은 주 리포트 중복")
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("같은 리포트에 대한 읽음 기록은 사용자마다 하나뿐이다")
        void weeklyReportReadIsUniquePerReportAndUser() {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            StudyRoomWeeklyReport report = saveWeeklyReport(fixture.room(), LocalDate.of(2026, 1, 5));
            weeklyReportReadRepository.saveAndFlush(
                    StudyRoomWeeklyReportRead.create(report, fixture.member(), LocalDateTime.now()));

            assertThatThrownBy(() -> weeklyReportReadRepository.saveAndFlush(
                    StudyRoomWeeklyReportRead.create(report, fixture.member(), LocalDateTime.now())))
                    .as("중복 읽음 기록")
                    .isInstanceOf(DataIntegrityViolationException.class);

            assertThat(weeklyReportReadRepository.existsByReportIdAndUserId(report.getId(), fixture.member().getId()))
                    .as("남아 있는 읽음 기록").isTrue();
        }
    }

    @Nested
    @DisplayName("연관관계 정리")
    class AssociationCleanup {

        @Test
        @DisplayName("방을 지우면 멤버십도 함께 사라진다")
        void deletingRoomRemovesMemberships() {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            Long roomId = fixture.roomId();

            inTransaction(() -> roomRepository.delete(roomRepository.findById(roomId).orElseThrow()));

            assertThat(roomRepository.findById(roomId)).as("삭제된 방").isEmpty();
            assertThat(memberRepository.countByRoomId(roomId)).as("남은 멤버십").isZero();
        }

        @Test
        @DisplayName("멤버가 방을 떠나도 방과 다른 멤버십은 남는다")
        void removingMembershipKeepsRoom() {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            Long roomId = fixture.roomId();

            inTransaction(() -> memberRepository.deleteByRoomIdAndUserId(roomId, fixture.member().getId()));

            assertThat(roomRepository.findById(roomId)).as("남아 있는 방").isPresent();
            assertThat(memberRepository.countByRoomId(roomId)).as("남은 멤버십").isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("식별자")
    class Identifiers {

        @Test
        @DisplayName("저장한 엔티티는 모두 식별자를 받는다")
        void savedEntitiesGetIdentifiers() {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            StudyRoomFeed feed = saveFeed(fixture.room(), fixture.host());
            StudyRoomFeedReaction feedReaction = saveFeedReaction(feed, fixture.host(), EMOJI);
            StudyRoomInviteCode inviteCode = saveValidInviteCode(fixture.room(), "123456");
            StudyRoomSharedProblem shared = saveSharedProblem(fixture.room(), fixture.host(),
                    saveProblem(fixture.host().getId()), null);
            StudyRoomSharedProblemComment comment = saveComment(shared, fixture.host(), "댓글");
            StudyRoomSharedProblemReaction sharedReaction = sharedProblemReactionRepository.saveAndFlush(
                    StudyRoomSharedProblemReaction.create(shared, fixture.member(), EMOJI));
            StudyRoomSharedProblemCommentReaction commentReaction = commentReactionRepository.saveAndFlush(
                    StudyRoomSharedProblemCommentReaction.create(comment, fixture.member(), EMOJI));
            StudyRoomWeeklyReport report = saveWeeklyReport(fixture.room(), LocalDate.of(2026, 1, 5));
            StudyRoomWeeklyReportRead read = weeklyReportReadRepository.saveAndFlush(
                    StudyRoomWeeklyReportRead.create(report, fixture.member(), LocalDateTime.now()));
            StudyRoomMember membership = memberRepository
                    .findByRoomIdAndUserId(fixture.roomId(), fixture.member().getId()).orElseThrow();

            assertThat(fixture.room().getId()).as("방 ID").isNotNull();
            assertThat(membership.getId()).as("멤버십 ID").isNotNull();
            assertThat(feed.getId()).as("피드 ID").isNotNull();
            assertThat(feedReaction.getId()).as("피드 리액션 ID").isNotNull();
            assertThat(inviteCode.getId()).as("초대 코드 ID").isNotNull();
            assertThat(shared.getId()).as("공유 문제 ID").isNotNull();
            assertThat(comment.getId()).as("댓글 ID").isNotNull();
            assertThat(sharedReaction.getId()).as("공유 문제 리액션 ID").isNotNull();
            assertThat(commentReaction.getId()).as("댓글 리액션 ID").isNotNull();
            assertThat(report.getId()).as("주간 리포트 ID").isNotNull();
            assertThat(read.getId()).as("읽음 기록 ID").isNotNull();
        }
    }
}
