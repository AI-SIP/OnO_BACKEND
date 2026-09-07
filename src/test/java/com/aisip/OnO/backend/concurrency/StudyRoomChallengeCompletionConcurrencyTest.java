package com.aisip.OnO.backend.concurrency;

import com.aisip.OnO.backend.studyroom.entity.StudyRoomChallenge;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomChallengeMetric;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomChallengeStatus;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomChallengeType;
import com.aisip.OnO.backend.studyroom.service.StudyRoomChallengeService;
import com.aisip.OnO.backend.studyroom.support.StudyRoomTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 챌린지 완료 판정의 동시 실행.
 *
 * <p>완료 전이는 방 멤버 전원에게 푸시를 보낸다. 완료 판정 경로는 세 군데
 * (활동 직후 훅, 챌린지 목록 조회, 주간 리포트 배치)이고 서로 다른 스레드에서 동시에 돌 수 있어,
 * 중복 전이를 막지 못하면 같은 알림이 사람 수 × 스레드 수만큼 나간다.
 *
 * <p>방어는 {@code tryTransitionFromInProgress} 하나다.
 * {@code UPDATE ... WHERE status = 'IN_PROGRESS'} 는 잠금 쓰기라 REPEATABLE READ 에서도
 * 항상 최신 커밋본을 보고 갱신하므로, 동시에 들어와도 1을 돌려받는 스레드는 하나뿐이어야 한다.
 * 그 전제가 실제로 지켜지는지를 여기서 고정한다.
 *
 * <p>FCM 은 실사용자 발송 경로라 베이스의 목으로만 확인한다. 실제 발송은 일어나지 않는다.
 */
@DisplayName("동시성 - 챌린지 완료 판정")
class StudyRoomChallengeCompletionConcurrencyTest extends StudyRoomTestSupport {

    private static final int THREAD_COUNT = 8;

    @Autowired
    private StudyRoomChallengeService challengeService;

    @Nested
    @DisplayName("완료 알림 중복 발송 방지")
    class DuplicateCompletionNotification {

        @Test
        @DisplayName("활동 직후 판정이 8번 동시에 돌아도 완료 알림은 멤버당 한 번만 나간다")
        void notifiesEachMemberOnceUnderConcurrentEvaluation() {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            StudyRoomChallenge challenge = saveChallenge(fixture.room(), "다 같이 1문제",
                    StudyRoomChallengeType.GROUP, StudyRoomChallengeMetric.PROBLEM_COUNT,
                    null, null, 1, LocalDateTime.now().minusHours(1), LocalDateTime.now().plusDays(7));
            saveProblem(fixture.host().getId());

            ConcurrentRunner.Outcome outcome = ConcurrentRunner.runConcurrently(
                    THREAD_COUNT,
                    () -> challengeService.checkAndNotifyChallengeCompletionForUser(fixture.host().getId()));

            assertThat(outcome.failures()).as("완료 판정은 실패할 이유가 없다").isEmpty();
            verify(fcmService).sendNotificationToAllUserDevice(eq(fixture.host().getId()), any());
            verify(fcmService).sendNotificationToAllUserDevice(eq(fixture.member().getId()), any());
            verify(fcmService, times(2))
                    .sendNotificationToAllUserDevice(any(), any());

            StudyRoomChallenge reloaded = challengeRepository.findById(challenge.getId()).orElseThrow();
            assertThat(reloaded.getStatus()).as("최종 상태").isEqualTo(StudyRoomChallengeStatus.COMPLETED);
            assertThat(reloaded.getCompletedAt()).as("완료 시각이 남아야 한다").isNotNull();
        }

        @Test
        @DisplayName("목록 조회와 활동 직후 판정이 섞여 들어와도 알림은 멤버당 한 번뿐이다")
        void notifiesOnceEvenWhenDifferentEntryPointsRaceEachOther() {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            saveChallenge(fixture.room(), "다 같이 1문제",
                    StudyRoomChallengeType.GROUP, StudyRoomChallengeMetric.PROBLEM_COUNT,
                    null, null, 1, LocalDateTime.now().minusHours(1), LocalDateTime.now().plusDays(7));
            saveProblem(fixture.member().getId());

            ConcurrentRunner.Outcome outcome = ConcurrentRunner.runConcurrently(THREAD_COUNT, index -> {
                if (index % 2 == 0) {
                    challengeService.checkAndNotifyChallengeCompletionForUser(fixture.member().getId());
                } else {
                    challengeService.getChallenges(fixture.roomId(), fixture.host().getId());
                }
            });

            assertThat(outcome.serverErrors())
                    .as("완료 전이 경합이 예외로 새어 나가면 사용자에게 500 이 나간다")
                    .isEmpty();
            verify(fcmService, times(2))
                    .sendNotificationToAllUserDevice(any(), any());
        }

        @Test
        @DisplayName("목표에 못 미치면 동시에 몇 번을 판정해도 알림이 나가지 않는다")
        void neverNotifiesWhileTargetIsUnmet() {
            RoomFixture fixture = createRoomWithMemberAndOutsider();
            StudyRoomChallenge challenge = saveChallenge(fixture.room(), "다 같이 50문제",
                    StudyRoomChallengeType.GROUP, StudyRoomChallengeMetric.PROBLEM_COUNT,
                    null, null, 50, LocalDateTime.now().minusHours(1), LocalDateTime.now().plusDays(7));
            saveProblem(fixture.host().getId());

            ConcurrentRunner.Outcome outcome = ConcurrentRunner.runConcurrently(
                    THREAD_COUNT,
                    () -> challengeService.checkAndNotifyChallengeCompletionForUser(fixture.host().getId()));

            assertThat(outcome.failures()).isEmpty();
            verify(fcmService, times(0)).sendNotificationToAllUserDevice(any(), any());
            assertThat(challengeRepository.findById(challenge.getId()).orElseThrow().getStatus())
                    .as("진행 중이어야 한다")
                    .isEqualTo(StudyRoomChallengeStatus.IN_PROGRESS);
        }
    }
}
