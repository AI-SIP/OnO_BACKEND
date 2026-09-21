package com.aisip.OnO.backend.problem.reminder;

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
 * 복습 알림 폴링 잡의 스케줄 등록 검증.
 *
 * <p>등록 내용(잡 키·크론·시간대)과 재등록 분기만 확인하면 되므로 스케줄러는 목으로 둔다.
 * 실제 스케줄러에 등록되는지는 {@code QuartzJobRegistrationTest} 가 확인한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProblemReviewReminderScheduler")
class ProblemReviewReminderSchedulerTest {

    private static final JobKey JOB_KEY = JobKey.jobKey("problem-review-reminder", "reminder");
    private static final TriggerKey TRIGGER_KEY = TriggerKey.triggerKey("problem-review-reminder-trigger", "reminder");
    private static final String CRON = "0 */5 * ? * *";

    @Mock
    private Scheduler quartzScheduler;

    private ProblemReviewReminderScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new ProblemReviewReminderScheduler(quartzScheduler);
    }

    @Nested
    @DisplayName("스케줄 등록")
    class Schedule {

        @Test
        @DisplayName("폴링 잡을 지속 잡으로 등록한다")
        void registersDurableJob() throws Exception {
            scheduler.schedule();

            ArgumentCaptor<JobDetail> jobCaptor = ArgumentCaptor.forClass(JobDetail.class);
            verify(quartzScheduler).addJob(jobCaptor.capture(), eq(true));

            JobDetail jobDetail = jobCaptor.getValue();
            assertThat(jobDetail.getKey()).isEqualTo(JOB_KEY);
            assertThat(jobDetail.getJobClass()).isEqualTo(ProblemReviewReminderJob.class);
            assertThat(jobDetail.isDurable())
                    .as("트리거 없이도 잡이 남아야 재기동 시 재등록할 수 있다")
                    .isTrue();
        }

        @Test
        @DisplayName("5분마다 서울 시각 기준으로 도는 크론 트리거를 만든다")
        void registersEveryFiveMinutesTrigger() throws Exception {
            given(quartzScheduler.checkExists(any(TriggerKey.class))).willReturn(false);

            scheduler.schedule();

            ArgumentCaptor<Trigger> triggerCaptor = ArgumentCaptor.forClass(Trigger.class);
            verify(quartzScheduler).scheduleJob(triggerCaptor.capture());

            CronTrigger trigger = (CronTrigger) triggerCaptor.getValue();
            assertThat(trigger.getKey()).isEqualTo(TRIGGER_KEY);
            assertThat(trigger.getJobKey()).isEqualTo(JOB_KEY);
            assertThat(trigger.getCronExpression()).isEqualTo(CRON);
            assertThat(trigger.getTimeZone()).isEqualTo(TimeZone.getTimeZone("Asia/Seoul"));
        }

        @Test
        @DisplayName("트리거가 이미 있으면 새로 만들지 않고 교체한다")
        void reschedulesExistingTrigger() throws Exception {
            given(quartzScheduler.checkExists(any(TriggerKey.class))).willReturn(true);

            scheduler.schedule();

            ArgumentCaptor<Trigger> triggerCaptor = ArgumentCaptor.forClass(Trigger.class);
            verify(quartzScheduler).rescheduleJob(eq(TRIGGER_KEY), triggerCaptor.capture());
            then(quartzScheduler).should(never()).scheduleJob(any(Trigger.class));

            assertThat(((CronTrigger) triggerCaptor.getValue()).getCronExpression())
                    .as("크론을 고쳤을 때 재배포만으로 반영되려면 교체가 일어나야 한다")
                    .isEqualTo(CRON);
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
