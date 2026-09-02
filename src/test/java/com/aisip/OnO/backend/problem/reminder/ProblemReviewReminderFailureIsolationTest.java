package com.aisip.OnO.backend.problem.reminder;

import com.aisip.OnO.backend.problem.event.ProblemCreatedEvent;
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

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
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
}
