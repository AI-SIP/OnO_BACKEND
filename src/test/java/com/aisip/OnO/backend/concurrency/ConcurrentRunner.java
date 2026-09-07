package com.aisip.OnO.backend.concurrency;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.common.exception.ErrorCase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 여러 스레드를 <b>정말로 동시에</b> 같은 지점에 밀어 넣는 러너.
 *
 * <p>스레드를 그냥 띄우면 첫 스레드가 스프링 프록시·커넥션 획득을 마칠 즈음에야 두 번째 스레드가
 * 출발해 사실상 순차 실행이 된다. 그래서 래치 세 개로 진입을 맞춘다.
 * ready 는 모든 스레드가 출발선에 섰음을, start 는 동시 출발 신호를, done 은 종료를 알린다.
 * 이 방식은 {@code ProductionIncidentRegressionTest} 의 태그 중복 재현에서 이미 검증됐다.
 *
 * <p>타임아웃은 반드시 건다. 락 경합이 잘못 풀려 스레드가 영영 깨어나지 않으면
 * 타임아웃 없이는 CI 가 통째로 멈춘다.
 */
final class ConcurrentRunner {

    private static final int READY_TIMEOUT_SECONDS = 10;
    private static final int DONE_TIMEOUT_SECONDS = 20;

    private ConcurrentRunner() {
    }

    static Outcome runConcurrently(int threadCount, Runnable action) {
        return runConcurrently(threadCount, index -> action.run());
    }

    /** {@code action} 은 0부터 시작하는 스레드 번호를 받는다. 스레드마다 다른 대상을 쓸 때 사용한다. */
    static Outcome runConcurrently(int threadCount, IntConsumer action) {
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger successCount = new AtomicInteger();

        try {
            for (int i = 0; i < threadCount; i++) {
                int index = i;
                executor.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        action.accept(index);
                        successCount.incrementAndGet();
                    } catch (Throwable throwable) {
                        failures.add(throwable);
                    } finally {
                        done.countDown();
                    }
                });
            }

            assertThat(ready.await(READY_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                    .as("모든 스레드가 출발선에 서지 못했다 - 스레드 풀이 부족한 상태로 잰 결과는 동시성 검증이 아니다")
                    .isTrue();
            start.countDown();
            assertThat(done.await(DONE_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                    .as("%d초 안에 끝나지 않았다 - 락 대기로 멈춰 있을 가능성이 크다", DONE_TIMEOUT_SECONDS)
                    .isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("동시 실행이 중단됐다", e);
        } finally {
            executor.shutdownNow();
        }

        return new Outcome(successCount.get(), List.copyOf(failures));
    }

    /**
     * 동시 실행 결과.
     *
     * @param successCount 예외 없이 끝난 스레드 수
     * @param failures     각 스레드가 던진 예외. {@link ApplicationException} 은 클라이언트에게
     *                     4xx 로 나가는 정상 거절이고, 그 밖은 전부 500 이다.
     */
    record Outcome(int successCount, List<Throwable> failures) {

        /** 사용자에게 500 으로 나가는 예외들. 비어 있지 않으면 그 자체가 장애다. */
        List<Throwable> serverErrors() {
            return failures.stream()
                    .filter(throwable -> !(throwable instanceof ApplicationException))
                    .toList();
        }

        List<ErrorCase> rejectedErrorCases() {
            return failures.stream()
                    .filter(ApplicationException.class::isInstance)
                    .map(throwable -> ((ApplicationException) throwable).getErrorCase())
                    .toList();
        }
    }
}
