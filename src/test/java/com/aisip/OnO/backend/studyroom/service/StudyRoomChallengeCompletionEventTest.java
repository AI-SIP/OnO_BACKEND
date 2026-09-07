package com.aisip.OnO.backend.studyroom.service;

import com.aisip.OnO.backend.studyroom.entity.*;
import com.aisip.OnO.backend.studyroom.support.StudyRoomTestSupport;
import com.aisip.OnO.backend.user.entity.User;
import com.aisip.OnO.backend.util.fcm.dto.NotificationRequestDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 학습 활동 직후 챌린지 완료를 즉시 확인하는 경로 검증.
 *
 * <p>{@code checkAndNotifyChallengeCompletionForUser} 는 문제 등록·복습 완료 이벤트를 받아
 * 그 사용자가 속한 모든 방의 진행 중 챌린지를 다시 판정한다. 완료로 넘어가면 방 멤버 전원에게
 * 푸시가 나가므로, 중복 발송과 오발송이 모두 문제가 된다.
 *
 * <p>FCM 은 실사용자 발송 경로다. 베이스에서 잡아 둔 목으로만 확인하고 실제로 호출되지 않는다.
 */
@DisplayName("StudyRoomChallengeService — 활동 직후 완료 판정")
class StudyRoomChallengeCompletionEventTest extends StudyRoomTestSupport {

    @Autowired
    private StudyRoomChallengeService challengeService;

    @Nested
    @DisplayName("완료 판정과 알림")
    class CompletionAndNotification {

        @Test
        @DisplayName("목표를 채우면 completed 로 전이하고 완료 시각이 남는다")
        void completionIsPersistedWithCompletedAt() {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            StudyRoomChallenge challenge = saveChallenge(fixture.room(), "각자 1문제",
                    StudyRoomChallengeType.INDIVIDUAL, StudyRoomChallengeMetric.PROBLEM_COUNT,
                    null, null, 1, LocalDateTime.now().minusHours(1), LocalDateTime.now().plusDays(7));
            saveProblem(fixture.host().getId());
            saveProblem(fixture.member().getId());

            challengeService.checkAndNotifyChallengeCompletionForUser(fixture.host().getId());

            StudyRoomChallenge reloaded = challengeRepository.findById(challenge.getId()).orElseThrow();
            assertThat(reloaded.getStatus()).as("전이된 상태").isEqualTo(StudyRoomChallengeStatus.COMPLETED);
            assertThat(reloaded.getCompletedAt())
                    .as("완료 시각 — 벌크 UPDATE 결과가 더티 체킹에 덮이면 안 된다")
                    .isNotNull();
        }

        @Test
        @DisplayName("완료 알림은 방 멤버 전원에게 한 번씩 발송된다")
        void everyMemberIsNotifiedOnce() {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            StudyRoomChallenge challenge = saveChallenge(fixture.room(), "다 같이 1문제",
                    StudyRoomChallengeType.GROUP, StudyRoomChallengeMetric.PROBLEM_COUNT,
                    null, null, 1, LocalDateTime.now().minusHours(1), LocalDateTime.now().plusDays(7));
            saveProblem(fixture.host().getId());

            challengeService.checkAndNotifyChallengeCompletionForUser(fixture.host().getId());

            ArgumentCaptor<NotificationRequestDto> dto = ArgumentCaptor.forClass(NotificationRequestDto.class);
            verify(fcmService).sendNotificationToAllUserDevice(eq(fixture.host().getId()), dto.capture());
            verify(fcmService).sendNotificationToAllUserDevice(eq(fixture.member().getId()), any());
            verify(fcmService, times(2)).sendNotificationToAllUserDevice(any(), any());

            assertThat(dto.getValue().title()).as("알림 제목").contains("챌린지 달성");
            assertThat(dto.getValue().body()).as("알림 본문").contains("다 같이 1문제");
            assertThat(dto.getValue().data())
                    .as("알림 데이터")
                    .containsEntry("type", "CHALLENGE_COMPLETED")
                    .containsEntry("roomId", String.valueOf(challenge.getRoom().getId()));
        }

        @Test
        @DisplayName("비멤버에게는 완료 알림이 가지 않는다")
        void outsiderIsNotNotified() {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            saveChallenge(fixture.room(), "다 같이 1문제", StudyRoomChallengeType.GROUP,
                    StudyRoomChallengeMetric.PROBLEM_COUNT, null, null, 1,
                    LocalDateTime.now().minusHours(1), LocalDateTime.now().plusDays(7));
            saveProblem(fixture.host().getId());

            challengeService.checkAndNotifyChallengeCompletionForUser(fixture.host().getId());

            verify(fcmService, times(0))
                    .sendNotificationToAllUserDevice(eq(fixture.outsider().getId()), any());
        }

        @Test
        @DisplayName("두 번 호출해도 완료 알림은 한 번만 나간다")
        void notificationIsNotSentTwice() {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            saveChallenge(fixture.room(), "다 같이 1문제", StudyRoomChallengeType.GROUP,
                    StudyRoomChallengeMetric.PROBLEM_COUNT, null, null, 1,
                    LocalDateTime.now().minusHours(1), LocalDateTime.now().plusDays(7));
            saveProblem(fixture.host().getId());

            challengeService.checkAndNotifyChallengeCompletionForUser(fixture.host().getId());
            challengeService.checkAndNotifyChallengeCompletionForUser(fixture.host().getId());

            verify(fcmService, times(2)).sendNotificationToAllUserDevice(any(), any());
        }

        @Test
        @DisplayName("목표에 못 미치면 상태도 그대로고 알림도 없다")
        void unfinishedChallengeStaysQuiet() {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            StudyRoomChallenge challenge = saveChallenge(fixture.room(), "각자 5문제",
                    StudyRoomChallengeType.INDIVIDUAL, StudyRoomChallengeMetric.PROBLEM_COUNT,
                    null, null, 5, LocalDateTime.now().minusHours(1), LocalDateTime.now().plusDays(7));
            saveProblem(fixture.host().getId());

            challengeService.checkAndNotifyChallengeCompletionForUser(fixture.host().getId());

            assertThat(challengeRepository.findById(challenge.getId()).orElseThrow().getStatus())
                    .as("상태").isEqualTo(StudyRoomChallengeStatus.IN_PROGRESS);
            verifyNoInteractions(fcmService);
        }

        @Test
        @DisplayName("종료 시각이 지난 챌린지는 알림 없이 expired 로 정리된다")
        void endedChallengeExpiresWithoutNotification() {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            StudyRoomChallenge challenge = saveChallenge(fixture.room(), "지난 챌린지",
                    StudyRoomChallengeType.INDIVIDUAL, StudyRoomChallengeMetric.PROBLEM_COUNT,
                    null, null, 100, LocalDateTime.now().minusDays(10), LocalDateTime.now().minusDays(1));

            challengeService.checkAndNotifyChallengeCompletionForUser(fixture.host().getId());

            assertThat(challengeRepository.findById(challenge.getId()).orElseThrow().getStatus())
                    .as("상태").isEqualTo(StudyRoomChallengeStatus.EXPIRED);
            verifyNoInteractions(fcmService);
        }

        @Test
        @DisplayName("출석 지표 챌린지는 학습한 날 수로 판정된다")
        void attendanceMetricIsEvaluated() {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            StudyRoomChallenge challenge = saveChallenge(fixture.room(), "출석 1일",
                    StudyRoomChallengeType.GROUP, StudyRoomChallengeMetric.ATTENDANCE,
                    null, null, 1, LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(7));
            saveProblem(fixture.host().getId());

            challengeService.checkAndNotifyChallengeCompletionForUser(fixture.host().getId());

            assertThat(challengeRepository.findById(challenge.getId()).orElseThrow().getStatus())
                    .as("출석 챌린지 상태").isEqualTo(StudyRoomChallengeStatus.COMPLETED);
        }

        @Test
        @DisplayName("한 사용자가 여러 방에 속하면 각 방의 챌린지가 모두 판정된다")
        void everyRoomOfTheUserIsEvaluated() {
            User user = fixtures.createUser("user");
            StudyRoom first = createRoom(user, "첫방");
            StudyRoom second = createRoom(user, "둘째방");
            StudyRoomChallenge firstChallenge = saveChallenge(first, "1문제",
                    StudyRoomChallengeType.GROUP, StudyRoomChallengeMetric.PROBLEM_COUNT,
                    null, null, 1, LocalDateTime.now().minusHours(1), LocalDateTime.now().plusDays(7));
            StudyRoomChallenge secondChallenge = saveChallenge(second, "1문제",
                    StudyRoomChallengeType.GROUP, StudyRoomChallengeMetric.PROBLEM_COUNT,
                    null, null, 1, LocalDateTime.now().minusHours(1), LocalDateTime.now().plusDays(7));
            saveProblem(user.getId());

            challengeService.checkAndNotifyChallengeCompletionForUser(user.getId());

            assertThat(challengeRepository.findById(firstChallenge.getId()).orElseThrow().getStatus())
                    .as("첫 방 챌린지").isEqualTo(StudyRoomChallengeStatus.COMPLETED);
            assertThat(challengeRepository.findById(secondChallenge.getId()).orElseThrow().getStatus())
                    .as("둘째 방 챌린지").isEqualTo(StudyRoomChallengeStatus.COMPLETED);
        }

        @Test
        @DisplayName("남의 방 챌린지는 건드리지 않는다")
        void otherRoomsChallengeIsUntouched() {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            StudyRoom otherRoom = createRoom(fixture.outsider(), "남의 방");
            StudyRoomChallenge otherChallenge = saveChallenge(otherRoom, "1문제",
                    StudyRoomChallengeType.GROUP, StudyRoomChallengeMetric.PROBLEM_COUNT,
                    null, null, 1, LocalDateTime.now().minusDays(10), LocalDateTime.now().minusDays(1));
            saveProblem(fixture.host().getId());

            challengeService.checkAndNotifyChallengeCompletionForUser(fixture.host().getId());

            assertThat(challengeRepository.findById(otherChallenge.getId()).orElseThrow().getStatus())
                    .as("남의 방 챌린지 상태 — 만료됐어도 내 활동으로는 정리되지 않는다")
                    .isEqualTo(StudyRoomChallengeStatus.IN_PROGRESS);
        }

        @Test
        @DisplayName("어느 방에도 속하지 않은 사용자의 활동은 아무 일도 일으키지 않는다")
        void roomlessUserIsNoOp() {
            User user = fixtures.createUser("lonely");

            assertThatCode(() -> challengeService.checkAndNotifyChallengeCompletionForUser(user.getId()))
                    .as("방 없는 사용자").doesNotThrowAnyException();
            verifyNoInteractions(fcmService);
        }

        @Test
        @DisplayName("진행 중인 챌린지가 없으면 통계를 조회하지 않고 끝난다")
        void noInProgressChallengeIsNoOp() {
            RoomFixture fixture = createRoomWithMemberAndOutsider();

            assertThatCode(() -> challengeService.checkAndNotifyChallengeCompletionForUser(fixture.host().getId()))
                    .as("챌린지 없는 방").doesNotThrowAnyException();
            verifyNoInteractions(fcmService);
        }

        @Test
        @DisplayName("한 멤버의 알림 발송이 실패해도 상태 전이는 유지된다")
        void notificationFailureDoesNotRollbackTransition() {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            StudyRoomChallenge challenge = saveChallenge(fixture.room(), "다 같이 1문제",
                    StudyRoomChallengeType.GROUP, StudyRoomChallengeMetric.PROBLEM_COUNT,
                    null, null, 1, LocalDateTime.now().minusHours(1), LocalDateTime.now().plusDays(7));
            saveProblem(fixture.host().getId());
            willThrow(new RuntimeException("FCM 장애"))
                    .given(fcmService).sendNotificationToAllUserDevice(eq(fixture.host().getId()), any());

            assertThatCode(() -> challengeService.checkAndNotifyChallengeCompletionForUser(fixture.host().getId()))
                    .as("발송 실패").doesNotThrowAnyException();

            assertThat(challengeRepository.findById(challenge.getId()).orElseThrow().getStatus())
                    .as("전이된 상태는 유지된다").isEqualTo(StudyRoomChallengeStatus.COMPLETED);
            verify(fcmService).sendNotificationToAllUserDevice(eq(fixture.member().getId()), any());
        }
    }
}
