package com.aisip.OnO.backend.problem.reminder;

import com.aisip.OnO.backend.problem.support.ProblemTestSupport;
import com.aisip.OnO.backend.user.entity.User;
import com.aisip.OnO.backend.util.fcm.dto.NotificationRequestDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;

import static com.aisip.OnO.backend.problem.reminder.ProblemReviewReminderStatus.CANCELED;
import static com.aisip.OnO.backend.problem.reminder.ProblemReviewReminderStatus.FAILED;
import static com.aisip.OnO.backend.problem.reminder.ProblemReviewReminderStatus.SCHEDULED;
import static com.aisip.OnO.backend.problem.reminder.ProblemReviewReminderStatus.SENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
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

    private User user;

    @BeforeEach
    void setUpUser() {
        user = fixtures.createUser();
    }

    private ProblemReviewReminder saveReminder(LocalDateTime scheduledAt) {
        return reminderRepository.saveAndFlush(ProblemReviewReminder.create(
                user.getId(), 1L, "메모", "출처", 1, 1, scheduledAt));
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

            sender.send(reminder, LocalDateTime.now());

            verify(fcmService, never()).sendNotificationToAllUserDevice(anyLong(), any(NotificationRequestDto.class));
            assertThat(reload(reminder.getId()).getStatus())
                    .as("선점에 실패했으면 남의 상태를 덮어써서는 안 된다")
                    .isEqualTo(SENT);
        }

        @Test
        @DisplayName("취소된 행도 선점되지 않는다")
        void doesNotSendCanceledRow() {
            ProblemReviewReminder reminder = saveReminder(LocalDateTime.now().minusHours(1));
            inTransaction(() -> reminderRepository.cancelByProblem(reminder.getProblemId(), CANCELED, List.of(SCHEDULED)));

            sender.send(reminder, LocalDateTime.now());

            verify(fcmService, never()).sendNotificationToAllUserDevice(anyLong(), any(NotificationRequestDto.class));
            assertThat(reload(reminder.getId()).getStatus()).isEqualTo(CANCELED);
        }

        @Test
        @DisplayName("SCHEDULED 인 행은 선점에 성공해 발송하고 SENT 로 남는다")
        void sendsScheduledRow() {
            LocalDateTime now = LocalDateTime.now();
            ProblemReviewReminder reminder = saveReminder(now.minusHours(1));

            sender.send(reminder, now);

            verify(fcmService).sendNotificationToAllUserDevice(anyLong(), any(NotificationRequestDto.class));
            ProblemReviewReminder sent = reload(reminder.getId());
            assertThat(sent.getStatus()).isEqualTo(SENT);
            assertThat(sent.getSentAt()).isNotNull();
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

            assertThatCode(() -> sender.send(reminder, LocalDateTime.now()))
                    .as("한 건의 실패가 배치 전체를 멈춰서는 안 된다")
                    .doesNotThrowAnyException();

            ProblemReviewReminder failed = reload(reminder.getId());
            assertThat(failed.getStatus()).isEqualTo(FAILED);
            assertThat(failed.getLastErrorMessage()).isNull();
            assertThat(failed.getRetryCount()).isEqualTo(1);
            assertThat(failed.getSentAt()).as("보내지 못했으므로 발송 시각은 없다").isNull();
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
