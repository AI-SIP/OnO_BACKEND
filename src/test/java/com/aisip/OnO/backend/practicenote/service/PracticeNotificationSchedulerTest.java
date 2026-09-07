package com.aisip.OnO.backend.practicenote.service;

import com.aisip.OnO.backend.practicenote.dto.PracticeNotificationRegisterDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.CronTrigger;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

/**
 * Quartz 연동 없이 스케줄러의 크론 변환과 예외 포장을 검증한다.
 *
 * <p>통합 테스트에서는 {@code QuartzConfig} 가 JDBC JobStore 를 강제해 실제 등록이 불가능하므로
 * (테스트 DB 에 {@code QRTZ_*} 테이블이 없다) 이 로직은 여기서 실제 구현으로 확인한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PracticeNotificationScheduler")
class PracticeNotificationSchedulerTest {

    private static final Long USER_ID = 7L;
    private static final Long PRACTICE_ID = 42L;

    @Mock
    private Scheduler quartzScheduler;

    private PracticeNotificationScheduler notificationScheduler;

    @BeforeEach
    void setUp() {
        notificationScheduler = new PracticeNotificationScheduler(quartzScheduler);
    }

    private String scheduleAndCaptureCron(PracticeNotificationRegisterDto dto) throws SchedulerException {
        notificationScheduler.schedulePracticeNotification(USER_ID, PRACTICE_ID, "복습 세트", dto);

        ArgumentCaptor<Trigger> triggerCaptor = ArgumentCaptor.forClass(Trigger.class);
        verify(quartzScheduler).scheduleJob(triggerCaptor.capture());
        return ((CronTrigger) triggerCaptor.getValue()).getCronExpression();
    }

    @Nested
    @DisplayName("스케줄 등록")
    class SchedulePracticeNotification {

        @Test
        @DisplayName("매일 반복은 지정한 시각의 매일 크론이 된다")
        void dailyCron() throws Exception {
            String cron = scheduleAndCaptureCron(new PracticeNotificationRegisterDto(1, 9, 30, "daily", null));

            assertThat(cron).isEqualTo("0 30 9 ? * *");
        }

        @Test
        @DisplayName("주간 반복은 선택한 요일 크론이 된다")
        void weeklyCron() throws Exception {
            String cron = scheduleAndCaptureCron(
                    new PracticeNotificationRegisterDto(7, 21, 0, "weekly", List.of(1, 3, 5)));

            assertThat(cron).isEqualTo("0 0 21 ? * MON,WED,FRI");
        }

        @Test
        @DisplayName("일요일까지 모든 요일을 변환한다")
        void weeklyCronForEveryWeekDay() throws Exception {
            String cron = scheduleAndCaptureCron(
                    new PracticeNotificationRegisterDto(7, 8, 5, "WEEKLY", List.of(1, 2, 3, 4, 5, 6, 7)));

            assertThat(cron).isEqualTo("0 5 8 ? * MON,TUE,WED,THU,FRI,SAT,SUN");
        }

        @Test
        @DisplayName("주간 반복인데 요일이 비어 있으면 매일로 되돌아간다")
        void weeklyWithoutWeekDaysFallsBackToDaily() throws Exception {
            String cron = scheduleAndCaptureCron(
                    new PracticeNotificationRegisterDto(7, 7, 0, "weekly", List.of()));

            assertThat(cron).isEqualTo("0 0 7 ? * *");
        }

        @Test
        @DisplayName("알 수 없는 반복 유형도 매일로 되돌아간다")
        void unknownRepeatTypeFallsBackToDaily() throws Exception {
            String cron = scheduleAndCaptureCron(
                    new PracticeNotificationRegisterDto(1, 23, 59, "NONE", null));

            assertThat(cron).isEqualTo("0 59 23 ? * *");
        }

        @Test
        @DisplayName("잡 식별자와 잡 데이터에 복습노트 정보를 담는다")
        void storesJobData() throws Exception {
            notificationScheduler.schedulePracticeNotification(USER_ID, PRACTICE_ID, "복습 세트", dailyDto());

            ArgumentCaptor<JobDetail> jobCaptor = ArgumentCaptor.forClass(JobDetail.class);
            verify(quartzScheduler).addJob(jobCaptor.capture(), anyBoolean());

            JobDetail jobDetail = jobCaptor.getValue();
            assertThat(jobDetail.getKey()).isEqualTo(JobKey.jobKey("practice-42", "practice-reminder"));
            assertThat(jobDetail.getJobClass()).isEqualTo(PracticeNotificationJob.class);
            assertThat(jobDetail.getJobDataMap().getLong("userId")).isEqualTo(USER_ID);
            assertThat(jobDetail.getJobDataMap().getLong("practiceId")).isEqualTo(PRACTICE_ID);
            assertThat(jobDetail.getJobDataMap().getString("practiceTitle")).isEqualTo("복습 세트");
        }

        @Test
        @DisplayName("요일 값이 1~7 범위를 벗어나면 예외가 발생한다")
        void invalidWeekDayThrows() {
            PracticeNotificationRegisterDto dto =
                    new PracticeNotificationRegisterDto(7, 9, 0, "weekly", List.of(8));

            assertThatThrownBy(() ->
                    notificationScheduler.schedulePracticeNotification(USER_ID, PRACTICE_ID, "복습 세트", dto))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid weekday");
        }

        @Test
        @DisplayName("Quartz 등록이 실패하면 스케줄 등록 실패로 감싼다")
        void wrapsSchedulerException() throws Exception {
            willThrow(new SchedulerException("boom")).given(quartzScheduler).addJob(any(), anyBoolean());

            assertThatThrownBy(() ->
                    notificationScheduler.schedulePracticeNotification(USER_ID, PRACTICE_ID, "복습 세트", dailyDto()))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("스케줄 등록 실패");
        }
    }

    @Nested
    @DisplayName("스케줄 삭제와 갱신")
    class DeleteAndUpdate {

        @Test
        @DisplayName("복습노트 id 로 만든 잡 키를 지운다")
        void deleteNotification() throws Exception {
            notificationScheduler.deleteNotification(PRACTICE_ID);

            verify(quartzScheduler).deleteJob(JobKey.jobKey("practice-42", "practice-reminder"));
        }

        @Test
        @DisplayName("Quartz 삭제가 실패하면 알림 삭제 실패로 감싼다")
        void wrapsDeleteException() throws Exception {
            willThrow(new SchedulerException("boom")).given(quartzScheduler).deleteJob(any());

            assertThatThrownBy(() -> notificationScheduler.deleteNotification(PRACTICE_ID))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("알림 삭제 실패");
        }

        @Test
        @DisplayName("갱신은 기존 잡을 지운 뒤 새로 등록한다")
        void updateNotificationDeletesThenSchedules() throws Exception {
            notificationScheduler.updateNotification(USER_ID, PRACTICE_ID, "복습 세트", dailyDto());

            InOrder inOrder = inOrder(quartzScheduler);
            inOrder.verify(quartzScheduler).deleteJob(JobKey.jobKey("practice-42", "practice-reminder"));
            inOrder.verify(quartzScheduler).addJob(any(), anyBoolean());
            inOrder.verify(quartzScheduler).scheduleJob(any(Trigger.class));
        }
    }

    private PracticeNotificationRegisterDto dailyDto() {
        return new PracticeNotificationRegisterDto(1, 9, 0, "daily", null);
    }
}
