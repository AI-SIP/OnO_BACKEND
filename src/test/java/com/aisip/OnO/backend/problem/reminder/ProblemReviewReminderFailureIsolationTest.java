package com.aisip.OnO.backend.problem.reminder;

import com.aisip.OnO.backend.problem.event.ProblemCreatedEvent;
import com.aisip.OnO.backend.util.fcm.dto.NotificationRequestDto;
import com.aisip.OnO.backend.util.fcm.service.FcmService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;

/**
 * 복습 알림 경로의 실패 격리 검증.
 *
 * <p>알림 예약은 문제 등록 트랜잭션이 커밋된 뒤 곁다리로 도는 작업이고, 폴링 잡은 5분마다 도는
 * 배치다. 둘 다 실패가 위로 전파되면 각각 "문제는 저장됐는데 응답이 500", "그 뒤 실행이 통째로
 * 중단"으로 이어진다. 실패를 삼키는지 확인하려면 저장소를 터뜨려야 해서 목으로 조립한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("복습 알림 실패 격리")
class ProblemReviewReminderFailureIsolationTest {

    @Mock
    private ProblemReviewReminderRepository repository;

    @Mock
    private FcmService fcmService;

    @Mock
    private ProblemReviewReminderSender sender;

    private ProblemReviewReminderService service;

    @BeforeEach
    void setUp() {
        service = new ProblemReviewReminderService(
                repository, new ProblemReviewReminderPolicy(), fcmService, sender);
    }

    private static ProblemCreatedEvent event() {
        return new ProblemCreatedEvent(1L, List.of(
                new ProblemCreatedEvent.ProblemData(100L, "메모", "출처", LocalDateTime.now())));
    }

    @Nested
    @DisplayName("등록 이벤트 처리")
    class HandleProblemCreated {

        @Test
        @DisplayName("예약 저장이 실패해도 예외가 밖으로 새어 나가지 않는다")
        void swallowsSchedulingFailure() {
            willThrow(new IllegalStateException("DB 장애"))
                    .given(repository).existsByProblemIdAndSequence(anyLong(), anyInt());

            assertThatCode(() -> service.handleProblemCreated(event()))
                    .as("알림 예약 실패가 문제 등록 응답까지 실패로 만들면 안 된다")
                    .doesNotThrowAnyException();

            then(repository).should(never()).saveAll(any());
        }

        @Test
        @DisplayName("정상이면 망각곡선 간격만큼 예약 행을 만든다")
        void schedulesEveryInterval() {
            given(repository.existsByProblemIdAndSequence(anyLong(), anyInt())).willReturn(false);

            service.handleProblemCreated(event());

            ArgumentCaptor<List<ProblemReviewReminder>> captor = ArgumentCaptor.forClass(List.class);
            then(repository).should().saveAll(captor.capture());
            assertThat(captor.getValue())
                    .extracting(ProblemReviewReminder::getIntervalDays)
                    .containsExactly(1, 3, 7, 14, 30);
        }

        @Test
        @DisplayName("이미 예약된 sequence 만 있으면 저장을 시도하지 않는다")
        void skipsWhenEverySequenceExists() {
            given(repository.existsByProblemIdAndSequence(anyLong(), anyInt())).willReturn(true);

            service.handleProblemCreated(event());

            then(repository).should(never()).saveAll(any());
        }
    }

    @Nested
    @DisplayName("폴링 잡")
    class PollingJob {

        private ProblemReviewReminderJob job;

        @Mock
        private ProblemReviewReminderService reminderService;

        @BeforeEach
        void setUpJob() {
            job = new ProblemReviewReminderJob();
            ReflectionTestUtils.setField(job, "reminderService", reminderService);
        }

        @Test
        @DisplayName("한국 시각 기준 현재 시각으로 due 알림 발송을 호출한다")
        void callsSendDueRemindersWithSeoulNow() {
            LocalDateTime before = LocalDateTime.now(ZoneId.of("Asia/Seoul"));

            job.executeInternal(null);

            ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
            then(reminderService).should().sendDueReminders(captor.capture());
            assertThat(captor.getValue())
                    .as("서버 시간대가 달라도 한국 시각으로 판단해야 한다")
                    .isBetween(before.minusMinutes(1), LocalDateTime.now(ZoneId.of("Asia/Seoul")).plusMinutes(1));
        }

        @Test
        @DisplayName("발송이 실패해도 잡은 예외 없이 끝나 다음 실행이 이어진다")
        void swallowsSendFailure() {
            willThrow(new IllegalStateException("FCM 장애"))
                    .given(reminderService).sendDueReminders(any());

            assertThatCode(() -> job.executeInternal(null))
                    .as("잡이 예외로 끝나면 Quartz 가 미스파이어로 처리해 다음 폴링이 밀린다")
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("폴링 발송")
    class SendDueReminders {

        /** 창(09~21시) 안의 고정 시각. */
        private final LocalDateTime now = LocalDateTime.of(2026, 7, 18, 12, 0);

        @Mock
        private PlatformTransactionManager transactionManager;

        private ProblemReviewReminder reminder(long id, long userId, long problemId, LocalDateTime scheduledAt) {
            ProblemReviewReminder reminder = ProblemReviewReminder.create(
                    userId, problemId, "메모", "출처", 1, 1, scheduledAt);
            ReflectionTestUtils.setField(reminder, "id", id);
            return reminder;
        }

        private ReminderPushCount pushCount(long userId, long count) {
            return new ReminderPushCount() {
                @Override
                public Long getUserId() {
                    return userId;
                }

                @Override
                public Long getPushCount() {
                    return count;
                }
            };
        }

        private void givenDue(ProblemReviewReminder... rows) {
            given(repository.findDueReminders(eq(ProblemReviewReminderStatus.SCHEDULED), eq(now), any()))
                    .willReturn(List.of(rows));
        }

        private void givenTodayPushes(ReminderPushCount... counts) {
            given(repository.countTodayPushesByUser(any(), any(), any(), any(), any())).willReturn(List.of(counts));
        }

        @Test
        @DisplayName("한 사용자의 적재가 실패해도 다음 사용자 묶음을 보내고 실패한 사용자 행만 FAILED 로 남긴다")
        void continuesWithNextUserAfterEnqueueFailure() {
            ProblemReviewReminderSender realSender = new ProblemReviewReminderSender(
                    repository, new ProblemReviewReminderPolicy(), fcmService, transactionManager);
            service = new ProblemReviewReminderService(
                    repository, new ProblemReviewReminderPolicy(), fcmService, realSender);
            givenDue(reminder(1L, 10L, 101L, now.minusMinutes(5)),
                    reminder(2L, 10L, 102L, now.minusMinutes(4)),
                    reminder(3L, 20L, 201L, now.minusMinutes(3)));
            givenTodayPushes();
            given(repository.tryUpdateStatus(anyLong(), any(), any(), any())).willReturn(1);
            // 사용자 20 호출은 스텁과 인자가 달라 strict stubbing 이 오류로 보므로 lenient 로 둔다.
            lenient().doThrow(new IllegalStateException("enqueue failed"))
                    .when(fcmService).sendNotificationToAllUserDevice(eq(10L), any(NotificationRequestDto.class));

            assertThatCode(() -> service.sendDueReminders(now)).doesNotThrowAnyException();

            then(fcmService).should().sendNotificationToAllUserDevice(eq(20L), any(NotificationRequestDto.class));
            then(repository).should().markFailed(1L, "enqueue failed", ProblemReviewReminderStatus.FAILED);
            then(repository).should().markFailed(2L, "enqueue failed", ProblemReviewReminderStatus.FAILED);
            then(repository).should().markSent(3L, now, ProblemReviewReminderStatus.SENT);
            then(repository).should(never()).markSent(eq(1L), any(), any());
            then(repository).should(never()).markFailed(eq(3L), any(), any());
        }

        @Test
        @DisplayName("선점 중 DB 예외가 나도 발송기는 예외를 던지지 않고 FCM 을 부르지 않는다")
        void swallowsClaimFailure() {
            ProblemReviewReminderSender realSender = new ProblemReviewReminderSender(
                    repository, new ProblemReviewReminderPolicy(), fcmService, transactionManager);
            willThrow(new IllegalStateException("lock wait timeout"))
                    .given(repository).tryUpdateStatus(anyLong(), any(), any(), any());

            assertThatCode(() -> realSender.send(10L, List.of(reminder(1L, 10L, 101L, now)), now))
                    .doesNotThrowAnyException();

            then(fcmService).should(never()).sendNotificationToAllUserDevice(anyLong(), any());
        }

        @Test
        @DisplayName("오늘 푸시가 상한(5)인 사용자는 건너뛰고 4인 사용자와 기록 없는 사용자에게는 자기 묶음을 넘긴다")
        void skipsOnlyUsersAtLimit() {
            ProblemReviewReminder atLimit = reminder(1L, 10L, 101L, now.minusMinutes(5));
            ProblemReviewReminder belowLimit = reminder(2L, 20L, 201L, now.minusMinutes(4));
            ProblemReviewReminder fresh1 = reminder(3L, 30L, 301L, now.minusMinutes(3));
            ProblemReviewReminder fresh2 = reminder(4L, 30L, 302L, now.minusMinutes(2));
            givenDue(atLimit, belowLimit, fresh1, fresh2);
            givenTodayPushes(pushCount(10L, 5L), pushCount(20L, 4L));

            service.sendDueReminders(now);

            then(sender).should(never()).send(eq(10L), any(), any());
            then(sender).should().send(20L, List.of(belowLimit), now);
            then(sender).should().send(30L, List.of(fresh1, fresh2), now);
        }

        @Test
        @DisplayName("상한을 넘긴 집계(6)도 건너뛴다")
        void skipsUsersOverLimit() {
            givenDue(reminder(1L, 10L, 101L, now.minusMinutes(5)));
            givenTodayPushes(pushCount(10L, 6L));

            service.sendDueReminders(now);

            then(sender).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("오늘 푸시 수는 due 가 있는 사용자만 대상으로 세고 하루 구간은 자정부터 다음 자정까지다")
        void countsOnlyDueUsersForToday() {
            givenDue(reminder(1L, 10L, 101L, now.minusMinutes(5)),
                    reminder(2L, 20L, 201L, now.minusMinutes(4)));
            givenTodayPushes();

            service.sendDueReminders(now);

            ArgumentCaptor<Collection<Long>> userIds = ArgumentCaptor.forClass(Collection.class);
            then(repository).should().countTodayPushesByUser(userIds.capture(),
                    eq(ProblemReviewReminderStatus.SENT), eq(ProblemReviewReminderStatus.SENDING),
                    eq(LocalDateTime.of(2026, 7, 18, 0, 0)), eq(LocalDateTime.of(2026, 7, 19, 0, 0)));
            assertThat(userIds.getValue()).containsExactlyInAnyOrder(10L, 20L);
        }

        @Test
        @DisplayName("발송 창 밖이면 due 조회도 발송도 하지 않는다")
        void doesNothingOutsideWindow() {
            LocalDateTime night = LocalDateTime.of(2026, 7, 18, 21, 0);

            service.sendDueReminders(night);

            then(repository).should(never()).findDueReminders(any(), any(), any());
            then(repository).should(never()).countTodayPushesByUser(any(), any(), any(), any(), any());
            then(sender).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("due 가 없으면 푸시 수를 세지 않는다")
        void skipsCountWhenNothingDue() {
            givenDue();

            service.sendDueReminders(now);

            then(repository).should(never()).countTodayPushesByUser(any(), any(), any(), any(), any());
            then(sender).shouldHaveNoInteractions();
        }
    }
}
