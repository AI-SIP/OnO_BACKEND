package com.aisip.OnO.backend.util.quartz;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerKey;
import org.quartz.impl.matchers.GroupMatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * ERROR 상태로 멈춘 Quartz 트리거를 주기적으로 WAITING 으로 되돌린다.
 *
 * <p>blue-green 배포 중에는 구 컨테이너가 수십 초 더 살아서 같은 QRTZ_ 테이블을 폴링한다.
 * 구 이미지에 없는 잡 클래스의 트리거를 집으면 {@code ClassNotFoundException} 으로 트리거가
 * ERROR 가 되는데, Quartz 는 기동 시 복구({@code recoverJobs})에서도 트리거 획득에서도
 * ERROR 를 다시 보지 않는다. 그대로 두면 다음 재기동까지 그 잡이 한 번도 돌지 않는다.
 * 기동 때 {@code rescheduleJob} 으로 트리거를 교체해도, 오류가 새 컨테이너 기동 뒤에 생기면 소용이 없다.
 *
 * <p>되돌릴 때는 이 인스턴스가 잡 클래스를 실제로 불러올 수 있는지 먼저 확인한다. 구 컨테이너에서
 * 이 점검이 돌면 자기가 모르는 잡을 되살렸다가 곧바로 다시 ERROR 로 만들기 때문이다.
 *
 * <p>blue 와 green 이 동시에 돌려도 안전하다. {@code resetTriggerFromErrorState} 는
 * {@code UPDATE ... WHERE TRIGGER_STATE = 'ERROR'} 한 문장이라, 먼저 바꾼 쪽만 반영되고 나머지는
 * 아무 일도 하지 않는다.
 *
 * <p>{@code @Scheduled} 대신 전용 스레드를 둔다. {@code @EnableScheduling} 을 켜면 Spring Boot 가
 * {@code ThreadPoolTaskScheduler} 빈을 만드는데, 지금은 {@code s3UploadExecutor} 때문에 기본
 * {@code TaskExecutor} 빈({@code applicationTaskExecutor})이 만들어지지 않아 그 스케줄러가
 * {@code @Async} 실행기로 잡힐 수 있다. 점검 하나 때문에 다른 비동기 경로의 실행기를 바꾸지 않는다.
 */
@Slf4j
@Component
public class QuartzErrorTriggerRecoverer {

    /** 새 컨테이너가 준비된 뒤 구 컨테이너가 내려갈 때까지 대략 1분이 걸린다. 그 뒤에 첫 점검을 한다. */
    static final Duration INITIAL_DELAY = Duration.ofMinutes(1);

    /**
     * 가장 촘촘한 폴링 잡(복습 리마인더)과 같은 5분이다. 되돌린 크론 트리거는 오발화 정책에 따라
     * 곧바로 한 번 실행되므로, 점검 간격만큼 늦어질 뿐 회차가 사라지지는 않는다.
     */
    static final Duration CHECK_INTERVAL = Duration.ofMinutes(5);

    private final Scheduler scheduler;
    private ScheduledExecutorService executor;

    @Autowired
    public QuartzErrorTriggerRecoverer(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    @EventListener(ApplicationReadyEvent.class)
    public synchronized void start() {
        if (executor != null) {
            return;
        }
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "quartz-error-trigger-recoverer");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(
                this::recoverSafely,
                INITIAL_DELAY.toMillis(),
                CHECK_INTERVAL.toMillis(),
                TimeUnit.MILLISECONDS
        );
    }

    @PreDestroy
    public synchronized void stop() {
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    /** 예외가 밖으로 새면 ScheduledExecutorService 가 이후 실행을 조용히 멈추므로 여기서 모두 막는다. */
    private void recoverSafely() {
        try {
            recoverErrorTriggers();
        } catch (Exception e) {
            log.error("[QuartzRecovery] ERROR 트리거 점검 실패", e);
        }
    }

    /**
     * ERROR 상태인 트리거를 찾아 되돌린다.
     *
     * @return 되돌린 트리거 수
     */
    public int recoverErrorTriggers() throws SchedulerException {
        int recovered = 0;
        for (String group : scheduler.getTriggerGroupNames()) {
            for (TriggerKey triggerKey : scheduler.getTriggerKeys(GroupMatcher.triggerGroupEquals(group))) {
                if (recoverIfError(triggerKey)) {
                    recovered++;
                }
            }
        }
        return recovered;
    }

    private boolean recoverIfError(TriggerKey triggerKey) {
        try {
            if (scheduler.getTriggerState(triggerKey) != Trigger.TriggerState.ERROR) {
                return false;
            }

            Trigger trigger = scheduler.getTrigger(triggerKey);
            if (trigger == null) {
                return false;
            }

            if (!canLoadJob(trigger)) {
                return false;
            }

            scheduler.resetTriggerFromErrorState(triggerKey);
            log.warn("[QuartzRecovery] ERROR 상태 트리거를 되돌렸습니다 - trigger: {}, job: {}",
                    triggerKey, trigger.getJobKey());
            return true;
        } catch (SchedulerException e) {
            log.error("[QuartzRecovery] 트리거 복구 실패 - trigger: {}", triggerKey, e);
            return false;
        }
    }

    private boolean canLoadJob(Trigger trigger) {
        try {
            JobDetail jobDetail = scheduler.getJobDetail(trigger.getJobKey());
            if (jobDetail == null) {
                log.warn("[QuartzRecovery] ERROR 트리거의 잡이 없어 되돌리지 않습니다 - trigger: {}, job: {}",
                        trigger.getKey(), trigger.getJobKey());
                return false;
            }
            return true;
        } catch (SchedulerException e) {
            log.warn("[QuartzRecovery] 이 인스턴스가 잡을 불러오지 못해 ERROR 트리거를 그대로 둡니다 - trigger: {}, job: {}, 원인: {}",
                    trigger.getKey(), trigger.getJobKey(), e.getMessage());
            return false;
        }
    }
}
