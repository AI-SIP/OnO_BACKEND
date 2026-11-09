package com.aisip.OnO.backend.problem.service;

import com.aisip.OnO.backend.common.ratelimit.RateLimitKeys;
import com.aisip.OnO.backend.common.ratelimit.RateLimitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 컨슈머 쪽에서 끝난 분석이 AI 를 쓰지 않았을 때(이미지 쪽 실패, 이미지 0장) 사용자 하루 한도 1회를 돌려준다.
 *
 * 상태 기록이 커밋된 뒤에 돌려준다. 기록이 롤백됐는데 한도만 돌려주면, 같은 메시지가 다시 처리될 때 한 번 더 돌려줄 수 있다.
 * 트랜잭션이 없으면 바로 돌려준다. RateLimitService.refund 는 Redis 장애에도 예외를 던지지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProblemAnalysisQuotaRefunder {

    private final RateLimitService rateLimitService;

    public void refundAfterCommit(Long userId) {
        if (userId == null) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            rateLimitService.refund(RateLimitKeys.AI_ANALYSIS, userId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                rateLimitService.refund(RateLimitKeys.AI_ANALYSIS, userId);
            }
        });
    }
}
