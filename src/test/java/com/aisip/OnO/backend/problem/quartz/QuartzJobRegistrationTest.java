package com.aisip.OnO.backend.problem.quartz;

import com.aisip.OnO.backend.problem.reminder.ProblemReviewReminderJob;
import com.aisip.OnO.backend.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.quartz.CronTrigger;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerKey;
import org.springframework.beans.factory.annotation.Autowired;

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
