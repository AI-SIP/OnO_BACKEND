package com.aisip.OnO.backend.common.transaction;

import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 캐시 무효화처럼 DB 반영이 끝난 뒤에 해야 하는 부가 작업을 커밋 후로 미룬다.
 *
 * <p>트랜잭션 안에서 캐시를 먼저 지우면, 커밋 전에 들어온 조회가 아직 반영되지 않은
 * 데이터로 캐시를 다시 채운다. 그 값이 TTL 이 끝날 때까지 나간다.
 *
 * <p>트랜잭션이 없으면 미룰 커밋이 없으니 바로 실행한다. 롤백되면 DB 가 그대로라
 * 캐시도 맞는 값이므로 실행하지 않는다.
 *
 * <p>작업이 실패해도 예외를 밖으로 내보내지 않는다. afterCommit 에서 예외가 나면
 * 이미 커밋된 요청이 실패 응답으로 바뀌고, 뒤에 등록된 다른 동기화도 건너뛴다.
 * 그래서 부가 작업에만 쓴다.
 */
@Slf4j
public final class AfterCommit {

    private AfterCommit() {
    }

    /**
     * @param taskName 실패 로그에 남길 작업 이름
     * @param task     커밋 후에 실행할 작업
     */
    public static void run(String taskName, Runnable task) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            runSafely(taskName, task);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                runSafely(taskName, task);
            }
        });
    }

    private static void runSafely(String taskName, Runnable task) {
        try {
            task.run();
        } catch (Exception e) {
            log.warn("커밋 후 작업에 실패했다. task={}, reason={}", taskName, e.getMessage(), e);
        }
    }
}
