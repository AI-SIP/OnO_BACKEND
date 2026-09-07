package com.aisip.OnO.backend.problem.quartz;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.CronTrigger;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerKey;

import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 스케줄 등록만 검증한다. 통합 테스트 환경은 인메모리 JobStore 에 auto-startup 이 꺼져 있어
 * 실제 트리거 발화를 확인할 수 없으므로, Quartz 스케줄러를 목으로 두고 등록 내용을 본다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReviewDueNotificationScheduler")
class ReviewDueNotificationSchedulerTest {

    private static final JobKey JOB_KEY = JobKey.jobKey("review-due-notification", "review");
    private static final TriggerKey TRIGGER_KEY = TriggerKey.triggerKey("review-due-trigger", "review");

    @Mock
    private Scheduler quartzScheduler;

    private ReviewDueNotificationScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new ReviewDueNotificationScheduler(quartzScheduler);
    }

    @Nested
    @DisplayName("스케줄 등록")
    class Schedule {

        @Test
        @DisplayName("복습 알림 잡을 지속 잡으로 등록한다")
        void registersDurableJob() throws Exception {
            scheduler.schedule();

            ArgumentCaptor<JobDetail> jobCaptor = ArgumentCaptor.forClass(JobDetail.class);
            verify(quartzScheduler).addJob(jobCaptor.capture(), eq(true));

            JobDetail jobDetail = jobCaptor.getValue();
            assertThat(jobDetail.getKey()).isEqualTo(JOB_KEY);
            assertThat(jobDetail.getJobClass()).isEqualTo(ReviewDueNotificationJob.class);
            assertThat(jobDetail.isDurable())
                    .as("트리거 없이도 잡이 남아 있어야 재기동 시 재등록이 가능하다")
                    .isTrue();
        }

        @Test
        @DisplayName("매일 서울 시각 오전 9시에 실행되는 크론 트리거를 만든다")
        void registersDailyNineAmSeoulTrigger() throws Exception {
            scheduler.schedule();

            ArgumentCaptor<Trigger> triggerCaptor = ArgumentCaptor.forClass(Trigger.class);
            verify(quartzScheduler).scheduleJob(triggerCaptor.capture());

            CronTrigger trigger = (CronTrigger) triggerCaptor.getValue();
            assertThat(trigger.getKey()).isEqualTo(TRIGGER_KEY);
            assertThat(trigger.getJobKey()).isEqualTo(JOB_KEY);
            assertThat(trigger.getCronExpression()).isEqualTo("0 0 9 ? * *");
            assertThat(trigger.getTimeZone())
                    .as("서버 시간대와 무관하게 한국 기준 오전 9시여야 한다")
                    .isEqualTo(TimeZone.getTimeZone("Asia/Seoul"));
        }

        @Test
        @DisplayName("트리거가 이미 있으면 새로 등록하지 않고 교체한다")
        void reschedulesExistingTrigger() throws Exception {
            given(quartzScheduler.checkExists(any(TriggerKey.class))).willReturn(true);

            scheduler.schedule();

            ArgumentCaptor<Trigger> triggerCaptor = ArgumentCaptor.forClass(Trigger.class);
            verify(quartzScheduler).rescheduleJob(eq(TRIGGER_KEY), triggerCaptor.capture());
            then(quartzScheduler).should(never()).scheduleJob(any(Trigger.class));

            assertThat(((CronTrigger) triggerCaptor.getValue()).getCronExpression())
                    .isEqualTo("0 0 9 ? * *");
        }

        @Test
        @DisplayName("트리거가 없으면 새로 등록한다")
        void schedulesNewTrigger() throws Exception {
            given(quartzScheduler.checkExists(any(TriggerKey.class))).willReturn(false);

            scheduler.schedule();

            then(quartzScheduler).should().scheduleJob(any(Trigger.class));
            then(quartzScheduler).should(never()).rescheduleJob(any(TriggerKey.class), any(Trigger.class));
        }
    }

    @Nested
    @DisplayName("등록 실패")
    class RegistrationFailure {

        @Test
        @DisplayName("스케줄러 예외가 나도 애플리케이션 기동을 막지 않는다")
        void swallowsSchedulerException() throws Exception {
            willThrow(new SchedulerException("job store unavailable"))
                    .given(quartzScheduler).addJob(any(JobDetail.class), anyBoolean());

            assertThatCode(() -> scheduler.schedule()).doesNotThrowAnyException();

            then(quartzScheduler).should(never()).scheduleJob(any(Trigger.class));
        }
    }
}
