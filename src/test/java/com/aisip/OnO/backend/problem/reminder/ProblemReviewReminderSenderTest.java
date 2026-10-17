package com.aisip.OnO.backend.problem.reminder;

import com.aisip.OnO.backend.problem.support.ProblemTestSupport;
import com.aisip.OnO.backend.user.entity.User;
import com.aisip.OnO.backend.util.fcm.dto.NotificationRequestDto;
import com.aisip.OnO.backend.util.fcm.support.BrokerOutageFcm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static com.aisip.OnO.backend.problem.reminder.ProblemReviewReminderStatus.CANCELED;
import static com.aisip.OnO.backend.problem.reminder.ProblemReviewReminderStatus.FAILED;
import static com.aisip.OnO.backend.problem.reminder.ProblemReviewReminderStatus.SCHEDULED;
import static com.aisip.OnO.backend.problem.reminder.ProblemReviewReminderStatus.SENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 복습 알림 발송기의 선점·실패 처리 검증.
 *
 * <p>발송기는 여러 인스턴스가 같은 행을 동시에 집어갈 수 있다는 전제로 만들어져 있다.
 * 조건부 UPDATE 로 선점에 실패하면 발송하지 않아야 하고, 발송이 터지면 배치를 멈추지 않고
 * 그 행만 FAILED 로 남겨야 한다. 둘 다 평상시 경로에서는 밟히지 않아 따로 확인한다.
 */
@DisplayName("ProblemReviewReminderSender")
class ProblemReviewReminderSenderTest extends ProblemTestSupport {

    @Autowired
    private ProblemReviewReminderSender sender;

    @Autowired
    private ProblemReviewReminderService reminderService;

    @Autowired
    private ProblemReviewReminderRepository reminderRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private User user;

    @BeforeEach
    void setUpUser() {
        user = fixtures.createUser();
    }

    private ProblemReviewReminder saveReminder(LocalDateTime scheduledAt) {
        return saveReminder(1L, scheduledAt);
    }

    private ProblemReviewReminder saveReminder(Long problemId, LocalDateTime scheduledAt) {
        return reminderRepository.saveAndFlush(ProblemReviewReminder.create(
                user.getId(), problemId, "메모", "출처", 1, 1, scheduledAt));
    }

    private NotificationRequestDto capturedRequest() {
        ArgumentCaptor<NotificationRequestDto> captor = ArgumentCaptor.forClass(NotificationRequestDto.class);
        verify(fcmService, times(1)).sendNotificationToAllUserDevice(eq(user.getId()), captor.capture());
        return captor.getValue();
    }

    /** 테스트 본문은 트랜잭션 밖이라 조회마다 영속성 컨텍스트가 새로 열린다. 벌크 UPDATE 결과가 그대로 보인다. */
    private ProblemReviewReminder reload(Long id) {
        return reminderRepository.findById(id).orElseThrow();
    }

    @Nested
    @DisplayName("선점")
    class Claim {

        @Test
        @DisplayName("다른 인스턴스가 이미 보낸 행은 선점에 실패해 FCM 을 부르지 않는다")
        void doesNotSendWhenClaimFails() {
            ProblemReviewReminder reminder = saveReminder(LocalDateTime.now().minusHours(1));
            inTransaction(() -> reminderRepository.markSent(reminder.getId(), LocalDateTime.now(), SENT));

            sender.send(user.getId(), List.of(reminder), LocalDateTime.now());

            verify(fcmService, never()).sendNotificationToAllUserDevice(anyLong(), any(NotificationRequestDto.class));
            assertThat(reload(reminder.getId()).getStatus())
                    .as("선점에 실패했으면 남의 상태를 덮어써서는 안 된다")
                    .isEqualTo(SENT);
        }

        @Test
        @DisplayName("취소된 행도 선점되지 않는다")
        void doesNotSendCanceledRow() {
            ProblemReviewReminder reminder = saveReminder(LocalDateTime.now().minusHours(1));
            inTransaction(() -> reminderRepository.cancelByIdIn(List.of(reminder.getId()), CANCELED));

            sender.send(user.getId(), List.of(reminder), LocalDateTime.now());

            verify(fcmService, never()).sendNotificationToAllUserDevice(anyLong(), any(NotificationRequestDto.class));
            assertThat(reload(reminder.getId()).getStatus()).isEqualTo(CANCELED);
        }

        @Test
        @DisplayName("SCHEDULED 인 행은 선점에 성공해 발송하고 SENT 로 남는다")
        void sendsScheduledRow() {
            LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
            ProblemReviewReminder reminder = saveReminder(now.minusHours(1));

            sender.send(user.getId(), List.of(reminder), now);

            verify(fcmService).sendNotificationToAllUserDevice(anyLong(), any(NotificationRequestDto.class));
            ProblemReviewReminder sent = reload(reminder.getId());
            assertThat(sent.getStatus()).isEqualTo(SENT);
            assertThat(sent.getSentAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("묶음 발송")
    class Bundle {

        @Test
        @DisplayName("묶음 중 일부가 이미 CANCELED/SENT 면 선점된 행만 보내고 count 도 선점 수다")
        void sendsOnlyClaimedRows() {
            LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
            ProblemReviewReminder canceled = saveReminder(201L, now.minusMinutes(40));
            ProblemReviewReminder alreadySent = saveReminder(202L, now.minusMinutes(30));
            ProblemReviewReminder first = saveReminder(203L, now.minusMinutes(20));
            ProblemReviewReminder second = saveReminder(204L, now.minusMinutes(10));
            inTransaction(() -> reminderRepository.cancelByIdIn(List.of(canceled.getId()), CANCELED));
            LocalDateTime otherSentAt = now.minusMinutes(1);
            inTransaction(() -> reminderRepository.markSent(alreadySent.getId(), otherSentAt, SENT));

            sender.send(user.getId(), List.of(second, alreadySent, first, canceled), now);

            NotificationRequestDto request = capturedRequest();
            assertThat(request.body()).isEqualTo("다시 풀어볼 오답이 2개 있어요");
            assertThat(request.data())
                    .as("선점에 실패한 행은 개수와 problemId 후보에서 빠진다")
                    .containsEntry("count", "2")
                    .containsEntry("problemId", "203");
            assertThat(reload(first.getId()).getStatus()).isEqualTo(SENT);
            assertThat(reload(second.getId()).getStatus()).isEqualTo(SENT);
            assertThat(reload(canceled.getId()).getStatus())
                    .as("선점에 실패한 행의 상태를 덮어쓰지 않는다")
                    .isEqualTo(CANCELED);
            assertThat(reload(alreadySent.getId()).getSentAt())
                    .as("이미 보낸 행의 발송 시각을 이번 폴링 시각으로 바꾸지 않는다")
                    .isEqualTo(otherSentAt);
        }

        @Test
        @DisplayName("선점된 행이 하나뿐이면 단건 알림으로 나간다")
        void sendsSingleNotificationWhenOnlyOneClaimed() {
            LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
            ProblemReviewReminder canceled = saveReminder(211L, now.minusMinutes(20));
            ProblemReviewReminder remaining = saveReminder(212L, now.minusMinutes(10));
            inTransaction(() -> reminderRepository.cancelByIdIn(List.of(canceled.getId()), CANCELED));

            sender.send(user.getId(), List.of(canceled, remaining), now);

            NotificationRequestDto request = capturedRequest();
            assertThat(request.body()).isEqualTo("그때 남긴 오답, 다시 풀어볼 시간이에요");
            assertThat(request.data()).containsEntry("problemId", "212").doesNotContainKey("count");
        }

        @Test
        @DisplayName("묶음 전부가 선점에 실패하면 FCM 을 부르지 않는다")
        void doesNotSendWhenNothingClaimed() {
            LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
            ProblemReviewReminder canceled = saveReminder(221L, now.minusMinutes(20));
            ProblemReviewReminder alreadySent = saveReminder(222L, now.minusMinutes(10));
            inTransaction(() -> reminderRepository.cancelByIdIn(List.of(canceled.getId()), CANCELED));
            inTransaction(() -> reminderRepository.markSent(alreadySent.getId(), now.minusMinutes(1), SENT));

            sender.send(user.getId(), List.of(canceled, alreadySent), now);

            verify(fcmService, never()).sendNotificationToAllUserDevice(anyLong(), any(NotificationRequestDto.class));
            assertThat(reload(canceled.getId()).getStatus()).isEqualTo(CANCELED);
            assertThat(reload(alreadySent.getId()).getStatus()).isEqualTo(SENT);
        }

        @Test
        @DisplayName("묶음이 성공하면 선점한 행이 모두 같은 sentAt 으로 SENT 가 된다")
        void marksAllClaimedRowsSentWithSameTimestamp() {
            LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
            ProblemReviewReminder a = saveReminder(231L, now.minusMinutes(30));
            ProblemReviewReminder b = saveReminder(232L, now.minusMinutes(20));
            ProblemReviewReminder c = saveReminder(233L, now.minusMinutes(10));

            sender.send(user.getId(), List.of(c, a, b), now);

            verify(fcmService, times(1)).sendNotificationToAllUserDevice(eq(user.getId()), any());
            List<ProblemReviewReminder> rows = List.of(reload(a.getId()), reload(b.getId()), reload(c.getId()));
            assertThat(rows).extracting(ProblemReviewReminder::getStatus).containsOnly(SENT);
            assertThat(rows).extracting(ProblemReviewReminder::getSentAt)
                    .as("하루 푸시 수를 sentAt 가짓수로 세므로 한 묶음은 같은 시각이어야 한다")
                    .containsOnly(rows.get(0).getSentAt())
                    .doesNotContainNull();
        }

        @Test
        @DisplayName("묶음 적재가 실패하면 선점한 행이 모두 FAILED 가 되고 예외를 던지지 않는다")
        void marksAllClaimedRowsFailedOnEnqueueFailure() {
            BrokerOutageFcm.create(transactionManager).routeFrom(fcmService);
            LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
            ProblemReviewReminder canceled = saveReminder(241L, now.minusMinutes(30));
            ProblemReviewReminder a = saveReminder(242L, now.minusMinutes(20));
            ProblemReviewReminder b = saveReminder(243L, now.minusMinutes(10));
            inTransaction(() -> reminderRepository.cancelByIdIn(List.of(canceled.getId()), CANCELED));

            assertThatCode(() -> sender.send(user.getId(), List.of(canceled, a, b), now))
                    .doesNotThrowAnyException();

            assertThat(List.of(reload(a.getId()), reload(b.getId()))).allSatisfy(row -> {
                assertThat(row.getStatus()).isEqualTo(FAILED);
                assertThat(row.getRetryCount()).isEqualTo(1);
                assertThat(row.getSentAt()).isNull();
            });
            assertThat(reload(canceled.getId()).getStatus())
                    .as("선점하지 않은 행은 실패 기록 대상이 아니다")
                    .isEqualTo(CANCELED);
        }
    }

    @Nested
    @DisplayName("발송 실패 기록")
    class FailureRecording {

        @Test
        @DisplayName("메시지 없는 예외로 실패해도 FAILED 로 남고 재시도 횟수가 오른다")
        void recordsFailureWithoutMessage() {
            doThrow(new RuntimeException())
                    .when(fcmService).sendNotificationToAllUserDevice(anyLong(), any(NotificationRequestDto.class));
            ProblemReviewReminder reminder = saveReminder(LocalDateTime.now().minusHours(1));

            assertThatCode(() -> sender.send(user.getId(), List.of(reminder), LocalDateTime.now()))
                    .as("한 건의 실패가 배치 전체를 멈춰서는 안 된다")
                    .doesNotThrowAnyException();

            ProblemReviewReminder failed = reload(reminder.getId());
            assertThat(failed.getStatus()).isEqualTo(FAILED);
            assertThat(failed.getLastErrorMessage()).isNull();
            assertThat(failed.getRetryCount()).isEqualTo(1);
            assertThat(failed.getSentAt()).as("보내지 못했으므로 발송 시각은 없다").isNull();
        }

        @Test
        @DisplayName("브로커 장애로 큐 적재가 실패하면 FAILED 로 남고 예외가 새어 나가지 않는다")
        void recordsFailureWhenBrokerIsDown() {
            BrokerOutageFcm.create(transactionManager).routeFrom(fcmService);
            ProblemReviewReminder reminder = saveReminder(LocalDateTime.now().minusHours(1));

            assertThatCode(() -> sender.send(user.getId(), List.of(reminder), LocalDateTime.now()))
                    .as("적재 실패가 발송기 밖으로 나가면 폴링 루프가 멈춘다")
                    .doesNotThrowAnyException();

            ProblemReviewReminder failed = reload(reminder.getId());
            assertThat(failed.getStatus())
                    .as("선점(SENDING)과 실패 기록이 함께 롤백돼 SCHEDULED 로 돌아가면 안 된다")
                    .isEqualTo(FAILED);
            assertThat(failed.getRetryCount()).isEqualTo(1);
            assertThat(failed.getLastErrorMessage()).isEqualTo("RabbitMQ FCM notification enqueue failed");
            assertThat(failed.getSentAt()).isNull();
        }
    }

    @Nested
    @DisplayName("서비스 단위 예외 처리")
    class ServiceLevelFailure {

        @Test
        @DisplayName("취소할 예약이 없는 사용자를 취소해도 아무 일도 일어나지 않는다")
        void cancelAllByUserWithoutPendingRows() {
            ProblemReviewReminder otherUsersReminder = reminderRepository.saveAndFlush(
                    ProblemReviewReminder.create(user.getId() + 1_000L, 2L, "메모", "출처", 1, 1,
                            LocalDateTime.now().plusDays(1)));

            assertThatCode(() -> reminderService.cancelAllByUser(user.getId())).doesNotThrowAnyException();

            assertThat(reload(otherUsersReminder.getId()).getStatus())
                    .as("다른 사용자의 예약은 그대로여야 한다")
                    .isEqualTo(SCHEDULED);
        }
    }
}
