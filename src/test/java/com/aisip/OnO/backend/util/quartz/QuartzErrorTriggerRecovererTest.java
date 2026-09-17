package com.aisip.OnO.backend.util.quartz;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobKey;
import org.quartz.JobPersistenceException;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.quartz.impl.matchers.GroupMatcher;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

/**
 * ERROR 트리거 복구의 분기 검증.
 *
 * <p>실제 JDBC 잡스토어에서 상태가 되돌아가는지는 {@code QuartzErrorTriggerRecoveryJdbcTest} 가 확인한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("QuartzErrorTriggerRecoverer")
class QuartzErrorTriggerRecovererTest {

    private static final JobKey JOB_KEY = JobKey.jobKey("problem-review-reminder", "reminder");
    private static final TriggerKey TRIGGER_KEY = TriggerKey.triggerKey("problem-review-reminder-trigger", "reminder");

    @Mock
    private Scheduler scheduler;

    private QuartzErrorTriggerRecoverer recoverer;

    @BeforeEach
    void setUp() {
        recoverer = new QuartzErrorTriggerRecoverer(scheduler);
    }

    private void givenSingleTrigger(TriggerKey key) throws SchedulerException {
        given(scheduler.getTriggerGroupNames()).willReturn(List.of(key.getGroup()));
        given(scheduler.getTriggerKeys(GroupMatcher.triggerGroupEquals(key.getGroup()))).willReturn(Set.of(key));
    }

    private Trigger triggerOf(TriggerKey key) {
        return TriggerBuilder.newTrigger().withIdentity(key).forJob(JOB_KEY).build();
    }

    @Test
    @DisplayName("ERROR 트리거의 잡을 불러올 수 있으면 되돌린다")
    void resetsErrorTrigger() throws Exception {
        givenSingleTrigger(TRIGGER_KEY);
        given(scheduler.getTriggerState(TRIGGER_KEY)).willReturn(Trigger.TriggerState.ERROR);
        given(scheduler.getTrigger(TRIGGER_KEY)).willReturn(triggerOf(TRIGGER_KEY));
        given(scheduler.getJobDetail(JOB_KEY)).willReturn(JobBuilder.newJob(Job.class).withIdentity(JOB_KEY).build());

        assertThat(recoverer.recoverErrorTriggers()).isEqualTo(1);

        then(scheduler).should().resetTriggerFromErrorState(TRIGGER_KEY);
    }

    @Test
    @DisplayName("ERROR 가 아닌 트리거는 건드리지 않는다")
    void ignoresHealthyTrigger() throws Exception {
        givenSingleTrigger(TRIGGER_KEY);
        given(scheduler.getTriggerState(TRIGGER_KEY)).willReturn(Trigger.TriggerState.NORMAL);

        assertThat(recoverer.recoverErrorTriggers()).isZero();

        then(scheduler).should(never()).resetTriggerFromErrorState(any());
    }

    @Test
    @DisplayName("이 인스턴스가 잡 클래스를 모르면 되돌리지 않는다 - 구 컨테이너가 되살렸다가 다시 ERROR 로 만드는 것을 막는다")
    void skipsWhenJobClassCannotBeLoaded() throws Exception {
        givenSingleTrigger(TRIGGER_KEY);
        given(scheduler.getTriggerState(TRIGGER_KEY)).willReturn(Trigger.TriggerState.ERROR);
        given(scheduler.getTrigger(TRIGGER_KEY)).willReturn(triggerOf(TRIGGER_KEY));
        willThrow(new JobPersistenceException("Couldn't retrieve job because a required class was not found"))
                .given(scheduler).getJobDetail(JOB_KEY);

        assertThat(recoverer.recoverErrorTriggers()).isZero();

        then(scheduler).should(never()).resetTriggerFromErrorState(any());
    }

    @Test
    @DisplayName("한 트리거 복구가 실패해도 나머지 트리거는 계속 점검한다")
    void continuesAfterFailure() throws Exception {
        TriggerKey broken = TriggerKey.triggerKey("broken", "reminder");
        given(scheduler.getTriggerGroupNames()).willReturn(List.of("reminder"));
        given(scheduler.getTriggerKeys(GroupMatcher.triggerGroupEquals("reminder")))
                .willReturn(new LinkedHashSet<>(List.of(broken, TRIGGER_KEY)));
        willThrow(new SchedulerException("db down")).given(scheduler).getTriggerState(broken);
        given(scheduler.getTriggerState(TRIGGER_KEY)).willReturn(Trigger.TriggerState.ERROR);
        given(scheduler.getTrigger(TRIGGER_KEY)).willReturn(triggerOf(TRIGGER_KEY));
        given(scheduler.getJobDetail(JOB_KEY)).willReturn(JobBuilder.newJob(Job.class).withIdentity(JOB_KEY).build());

        assertThat(recoverer.recoverErrorTriggers()).isEqualTo(1);

        then(scheduler).should().resetTriggerFromErrorState(TRIGGER_KEY);
    }
}
