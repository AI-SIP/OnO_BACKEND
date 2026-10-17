package com.aisip.OnO.backend.studyroom.service;

import com.aisip.OnO.backend.studyroom.event.StudyRoomActivityEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.util.concurrent.ThreadPoolExecutor;

import static org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT;

/**
 * 학습 활동이 커밋된 뒤 스터디룸 피드를 남기고 챌린지 달성을 판정한다.
 *
 * <p><b>왜 요청 스레드에서 돌리지 않나.</b> 예전에는 {@code AFTER_COMMIT} 리스너가 {@code REQUIRES_NEW} 로
 * 피드를 만들고, 그 안에서 챌린지 판정이 다시 {@code REQUIRES_NEW} 를 열었다. 바깥 트랜잭션의 커넥션은
 * {@code afterCommit} 콜백이 끝난 뒤 {@code cleanupAfterCompletion} 에서야 반납되고, open-in-view 가 켜져 있으면
 * 요청이 끝날 때까지 남는다. 그래서 요청 하나가 커넥션을 <b>세 개까지</b> 동시에 쥐었고, 풀 크기만큼 등록이 겹치면
 * 모두 첫 커넥션을 쥔 채 다음 커넥션을 기다리며 30초 뒤에 함께 실패했다. (#405)
 * {@code MissionProgressUpdater} 가 같은 이유로 {@code REQUIRES_NEW} 를 되돌렸고, 그 주석이 대안으로 남긴 것이
 * "별도 실행기로 요청 스레드에서 떼어내기" 다. 여기서 그 방법을 쓴다.
 *
 * <p><b>커넥션 수.</b> 요청 스레드는 작업을 실행기에 넘기기만 하고 커넥션을 더 받지 않는다.
 * 실행기에서는 피드 생성과 챌린지 판정을 <b>차례로, 각자의 트랜잭션으로</b> 돌린다. 바깥 트랜잭션이 없으니
 * 한 작업이 쥐는 커넥션은 한 번에 하나이고, 뒤처리 전체가 쓰는 커넥션은 실행기 스레드 수({@value #WORKERS}개)를
 * 넘지 않는다. 등록이 몰리면 뒤처리는 큐에서 기다릴 뿐 사용자 요청의 커넥션을 빼앗지 않는다.
 *
 * <p><b>지키는 것.</b> 원래 등록이 롤백되면 이벤트가 오지 않으므로 피드도 판정도 돌지 않는다.
 * 피드 생성이나 챌린지 판정이 실패해도 이미 커밋된 등록에는 영향이 없고, 둘은 서로의 실패에도 영향을 받지 않는다.
 * 챌린지 달성 알림(FCM 큐 적재)은 예전처럼 판정 트랜잭션이 커밋된 뒤에 나간다. 바뀐 것은 실행 스레드뿐이다.
 *
 * <p><b>받아들인 것.</b> 피드와 달성 알림이 응답보다 조금 늦게 생긴다. 큐가 가득 차면(평소에는 일어나지 않을 양이다)
 * 그 활동의 피드와 판정은 건너뛰고 경고 로그만 남긴다. 챌린지 상태는 목록 조회와 주간 배치에서도 다시 판정하므로
 * 영영 갱신되지 않는 것은 아니다. 배포로 컨테이너가 내려갈 때는 남은 작업을 잠시 기다렸다가 내려간다.
 */
@Slf4j
@Component
public class StudyRoomActivityListener implements DisposableBean {

    /** 뒤처리가 동시에 쓰는 커넥션 상한이기도 하다. 운영 풀 10개 중 사용자 요청 몫을 남겨 두는 크기다. */
    static final int WORKERS = 2;

    /** 활동 하나의 뒤처리는 수십 ms 다. 이만큼 밀리는 것은 장애 상황이라 그때는 버리고 로그를 남긴다. */
    private static final int QUEUE_CAPACITY = 1000;

    private static final int SHUTDOWN_AWAIT_SECONDS = 10;

    private final StudyRoomFeedService feedService;
    private final StudyRoomChallengeService challengeService;
    private final ThreadPoolTaskExecutor executor;

    public StudyRoomActivityListener(StudyRoomFeedService feedService, StudyRoomChallengeService challengeService) {
        this.feedService = feedService;
        this.challengeService = challengeService;
        this.executor = createExecutor();
    }

    /**
     * 실행기를 빈으로 등록하지 않는다. {@code TaskExecutor} 빈이 하나 생기면 실행기 이름을 적지 않은
     * {@code @Async} 가 모두 이 실행기로 몰린다(지금은 기본 실행기 빈이 없다, {@code AsyncConfig} 참고).
     */
    private static ThreadPoolTaskExecutor createExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(WORKERS);
        executor.setMaxPoolSize(WORKERS);
        executor.setQueueCapacity(QUEUE_CAPACITY);
        executor.setThreadNamePrefix("study-room-activity-");
        executor.setRejectedExecutionHandler((task, pool) ->
                log.warn("스터디룸 활동 뒤처리 큐가 가득 차 이번 활동의 피드와 챌린지 판정을 건너뛴다. queue: {}",
                        pool.getQueue().size()));
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(SHUTDOWN_AWAIT_SECONDS);
        executor.initialize();
        return executor;
    }

    @TransactionalEventListener(phase = AFTER_COMMIT)
    public void onActivity(StudyRoomActivityEvent event) {
        executor.execute(() -> process(event));
    }

    void process(StudyRoomActivityEvent event) {
        try {
            feedService.createFeedsForUserRooms(event.userId(), event.eventType(), event.metadata());
        } catch (Exception e) {
            log.error("Failed to create study room feed. userId: {}, eventType: {}", event.userId(), event.eventType(), e);
        }
        try {
            challengeService.checkAndNotifyChallengeCompletionForUser(event.userId());
        } catch (Exception e) {
            log.error("Failed to check challenge completion. userId: {}, eventType: {}", event.userId(), event.eventType(), e);
        }
    }

    /**
     * 넘겨 둔 뒤처리가 모두 끝날 때까지 기다린다. 끝나면 {@code true}.
     *
     * <p>테스트가 뒤처리 결과를 확인하거나, 다음 테스트의 DB 정리와 뒤처리가 겹치지 않게 할 때 쓴다.
     */
    public boolean awaitIdle(Duration timeout) {
        ThreadPoolExecutor pool = executor.getThreadPoolExecutor();
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            // 큐와 실행 중 개수만 보면 작업을 큐에서 꺼낸 직후 아직 실행 전인 순간을 놓친다. 넣은 수와 끝난 수를 맞춰 본다.
            if (pool.getCompletedTaskCount() == pool.getTaskCount()) {
                return true;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    /**
     * 이 빈은 피드와 챌린지 서비스(그리고 그 뒤의 DataSource)보다 먼저 내려간다.
     * 여기서 남은 작업을 기다려야 DataSource 가 닫힌 뒤에 뒤처리가 도는 일이 없다.
     */
    @Override
    public void destroy() {
        executor.shutdown();
    }
}
