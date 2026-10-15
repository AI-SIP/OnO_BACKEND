package com.aisip.OnO.backend.problem.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;

/**
 * 같은 문제의 분석 메시지가 겹쳐 나가지 않게 막는 Redis 표시.
 *
 * 앱의 "다시 분석하기"와 이미지 수정 API 는 매번 분석을 요청한다. 이미 보낸 메시지가 처리 중인데
 * 또 보내면 하루 한도를 한 번 더 쓰고 OpenAI 도 두 번 부른다. 그래서 메시지를 보낼 때 SET NX 로
 * 표시를 남기고, 표시가 있는 동안 들어온 요청은 한도도 쓰지 않고 메시지도 보내지 않는다.
 * 동시에 두 요청이 들어와도 SET NX 에 성공한 쪽만 보낸다.
 *
 * TTL 은 35분이다. 메시지는 큐에서 최대 30분(큐 TTL, RabbitMQConfig) 기다린 뒤 꺼내지면 처리에 최대 약 3.5분
 * (OpenAI read 타임아웃 60초 x 3회 + 백오프 1초, 2초 + S3 읽기)이 걸린다. TTL 이 30분이면 큐에서 오래 기다린 메시지가
 * 처리되는 동안 표시가 먼저 풀려 같은 문제의 메시지가 겹쳐 나갈 수 있으므로 그보다 길게 둔다.
 * 35분이 지난 PROCESSING 은 처리 중이 아니라 멈춘 것으로 보고 다시 요청을 받는다.
 * 분석이 끝나면(성공, 최종 실패, 전송 실패) 표시를 지워 바로 다시 요청할 수 있게 한다.
 *
 * Redis 장애 시에는 RateLimitService 처럼 통과시킨다. 중복 메시지가 나갈 수 있지만 컨슈머가
 * COMPLETED 를 다시 분석하지 않으므로 결과가 망가지지는 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProblemAnalysisDispatchGuard {

    static final String KEY_PREFIX = "ai_analysis_inflight:";
    static final Duration TTL = Duration.ofMinutes(35);

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * @return 이번 요청이 메시지를 보내도 되면 true. 이미 처리 중인 메시지가 있으면 false
     */
    public boolean tryAcquire(Long problemId) {
        try {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key(problemId), "1", TTL);
            // 파이프라인·트랜잭션 안이 아니면 null 이 오지 않지만, 오면 막지 않는다.
            return !Boolean.FALSE.equals(acquired);
        } catch (Exception e) {
            log.warn("Analysis dispatch guard check failed, failing open - problemId: {}, reason: {}",
                    problemId, e.getMessage());
            return true;
        }
    }

    public void release(Long problemId) {
        try {
            redisTemplate.delete(key(problemId));
        } catch (Exception e) {
            // 지우지 못해도 TTL(35분)이 지나면 풀린다.
            log.warn("Analysis dispatch guard release failed - problemId: {}, reason: {}",
                    problemId, e.getMessage());
        }
    }

    /**
     * 지금 트랜잭션이 커밋된 뒤에 표시를 지운다. 커밋 전에 지우면 그 사이에 들어온 요청이
     * 아직 PROCESSING 인 행을 보고 메시지를 또 보낸 뒤, 이 트랜잭션의 커밋이 그 위에 상태를 덮는다.
     * 트랜잭션이 없으면 바로 지운다.
     */
    public void releaseAfterCommit(Long problemId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            release(problemId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                release(problemId);
            }
        });
    }

    static String key(Long problemId) {
        return KEY_PREFIX + problemId;
    }
}
