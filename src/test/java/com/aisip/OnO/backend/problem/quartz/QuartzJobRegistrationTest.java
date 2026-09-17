package com.aisip.OnO.backend.problem.quartz;

import com.aisip.OnO.backend.practicenote.service.PracticeNotificationJob;
import com.aisip.OnO.backend.problem.reminder.ProblemReviewReminderJob;
import com.aisip.OnO.backend.studyroom.quartz.ChallengeNotificationJob;
import com.aisip.OnO.backend.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.quartz.CronTrigger;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.quartz.core.QuartzScheduler;
import org.quartz.spi.JobFactory;
import org.quartz.spi.OperableTrigger;
import org.quartz.spi.TriggerFiredBundle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 애플리케이션이 뜰 때 복습 관련 Quartz 잡이 실제 스케줄러에 등록되는지 확인한다.
 *
 * <p>등록 코드는 {@code @PostConstruct} 에서 한 번 돌고 끝나서, 스케줄러를 목으로 바꿔 끼운
 * 단위 테스트만으로는 "진짜 스케줄러에도 들어갔는지"를 알 수 없다. 예전에는 테스트 프로필이
 * 메모리 잡스토어를 선언해도 코드가 JDBC 잡스토어를 강제해 QRTZ_ 테이블이 없는 테스트 DB 에서
 * 등록이 통째로 실패했고, 그 사실을 아무도 눈치채지 못했다.
 *
 * <p>테스트 프로필은 {@code auto-startup: false} 라 스케줄러가 대기 상태다. 잡이 실제로 발화하지
 * 않으므로 이 테스트가 FCM 을 발송할 일은 없다.
 */
@DisplayName("Quartz 잡 등록")
class QuartzJobRegistrationTest extends IntegrationTestSupport {

    @Autowired
    private Scheduler scheduler;

    private CronTrigger cronTriggerOf(JobKey jobKey) throws SchedulerException {
        List<? extends Trigger> triggers = scheduler.getTriggersOfJob(jobKey);
        assertThat(triggers).as("잡에 트리거가 하나는 붙어 있어야 실행된다").hasSize(1);
        return (CronTrigger) triggers.get(0);
    }

    @Nested
    @DisplayName("복습 알림 잡")
    class ReviewDueNotification {

        private final JobKey jobKey = JobKey.jobKey("review-due-notification", "review");

        @Test
        @DisplayName("잡이 스케줄러에 등록돼 있다")
        void jobIsRegistered() throws Exception {
            assertThat(scheduler.checkExists(jobKey)).isTrue();

            JobDetail jobDetail = scheduler.getJobDetail(jobKey);
            assertThat(jobDetail.getJobClass()).isEqualTo(ReviewDueNotificationJob.class);
            assertThat(jobDetail.isDurable()).isTrue();
        }

        @Test
        @DisplayName("서울 시각 매일 오전 9시 크론 트리거가 붙어 있다")
        void triggerFiresAtNineAmSeoul() throws Exception {
            assertThat(scheduler.checkExists(TriggerKey.triggerKey("review-due-trigger", "review"))).isTrue();

            CronTrigger trigger = cronTriggerOf(jobKey);
            assertThat(trigger.getCronExpression()).isEqualTo("0 0 9 ? * *");
            assertThat(trigger.getTimeZone()).isEqualTo(TimeZone.getTimeZone("Asia/Seoul"));
        }
    }

    @Nested
    @DisplayName("복습 알림 폴링 잡")
    class ReviewReminderPolling {

        private final JobKey jobKey = JobKey.jobKey("problem-review-reminder", "reminder");

        @Test
        @DisplayName("잡이 스케줄러에 등록돼 있다")
        void jobIsRegistered() throws Exception {
            assertThat(scheduler.checkExists(jobKey)).isTrue();
            assertThat(scheduler.getJobDetail(jobKey).getJobClass()).isEqualTo(ProblemReviewReminderJob.class);
        }

        @Test
        @DisplayName("5분마다 도는 크론 트리거가 붙어 있다")
        void triggerFiresEveryFiveMinutes() throws Exception {
            CronTrigger trigger = cronTriggerOf(jobKey);

            assertThat(trigger.getCronExpression()).isEqualTo("0 */5 * ? * *");
            assertThat(trigger.getTimeZone()).isEqualTo(TimeZone.getTimeZone("Asia/Seoul"));
        }
    }

    /**
     * 발화 시각이 되면 스케줄러는 자기 JobFactory 로 잡 인스턴스를 만든 뒤 execute 를 부른다.
     * 잡을 빈으로 주입받아 execute 를 직접 부르는 테스트는 이 생성 단계를 건너뛰어, 생성자 주입 잡이
     * 인스턴스화에 실패해 트리거가 ERROR 로 바뀌는 문제를 잡지 못했다.
     *
     * <p>스케줄러를 시작하면 등록된 잡이 실제로 돌기 때문에, 스케줄러가 쓰는 JobFactory 를 꺼내
     * 발화 때와 같은 {@link TriggerFiredBundle} 로 인스턴스만 만든다. execute 는 부르지 않는다.
     */
    @Nested
    @DisplayName("발화 시 잡 인스턴스 생성")
    class JobInstantiation {

        private Job newJobLikeFiring(JobDetail jobDetail) throws SchedulerException {
            QuartzScheduler quartzScheduler = (QuartzScheduler) ReflectionTestUtils.getField(scheduler, "sched");
            JobFactory jobFactory = quartzScheduler.getJobFactory();

            OperableTrigger trigger = (OperableTrigger) TriggerBuilder.newTrigger()
                    .forJob(jobDetail)
                    .withSchedule(SimpleScheduleBuilder.simpleSchedule())
                    .build();
            Date now = new Date();
            TriggerFiredBundle bundle = new TriggerFiredBundle(
                    jobDetail, trigger, null, false, now, now, null, null);

            return jobFactory.newJob(bundle, scheduler);
        }

        private JobDetail jobDetailOf(Class<? extends Job> jobClass) {
            return JobBuilder.newJob(jobClass).withIdentity("instantiation-test-" + jobClass.getSimpleName()).build();
        }

        @Test
        @DisplayName("생성자 주입을 쓰는 챌린지 알림 잡도 의존성이 채워진 채로 만들어진다")
        void challengeNotificationJob() throws Exception {
            JobDetail jobDetail = JobBuilder.newJob(ChallengeNotificationJob.class)
                    .withIdentity("instantiation-test-challenge")
                    .usingJobData("roomId", "1")
                    .usingJobData("challengeTitle", "챌린지")
                    .usingJobData("notificationType", "HALFWAY")
                    .build();

            Job job = newJobLikeFiring(jobDetail);

            assertThat(job).isInstanceOf(ChallengeNotificationJob.class);
            assertThat(job).extracting("memberRepository").isNotNull();
            assertThat(job).extracting("fcmService").isNotNull();
        }

        @Test
        @DisplayName("필드 주입을 쓰는 기존 잡들도 그대로 의존성이 채워진다")
        void fieldInjectedJobs() throws Exception {
            Job reviewDue = newJobLikeFiring(jobDetailOf(ReviewDueNotificationJob.class));
            assertThat(reviewDue).isInstanceOf(ReviewDueNotificationJob.class);
            assertThat(AopTestUtils.<Object>getUltimateTargetObject(reviewDue))
                    .extracting("problemRepository", "userRepository", "fcmService")
                    .doesNotContainNull();

            Job reminder = newJobLikeFiring(jobDetailOf(ProblemReviewReminderJob.class));
            assertThat(reminder).isInstanceOf(ProblemReviewReminderJob.class);
            assertThat(AopTestUtils.<Object>getUltimateTargetObject(reminder))
                    .extracting("reminderService")
                    .isNotNull();

            Job practice = newJobLikeFiring(jobDetailOf(PracticeNotificationJob.class));
            assertThat(practice).isInstanceOf(PracticeNotificationJob.class);
            assertThat(AopTestUtils.<Object>getUltimateTargetObject(practice))
                    .extracting("fcmService")
                    .isNotNull();
        }
    }

    @Nested
    @DisplayName("테스트 환경 안전장치")
    class TestEnvironmentGuard {

        @Test
        @DisplayName("스케줄러는 대기 상태라 등록된 잡이 저절로 실행되지 않는다")
        void schedulerIsNotStarted() throws Exception {
            assertThat(scheduler.isStarted())
                    .as("테스트 중 잡이 발화하면 FCM 발송 경로가 예고 없이 돌게 된다")
                    .isFalse();
            assertThat(scheduler.isShutdown()).isFalse();
        }
    }
}
