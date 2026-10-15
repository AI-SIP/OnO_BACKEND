package com.aisip.OnO.backend.problem.service;

import com.aisip.OnO.backend.common.ratelimit.RateLimitService;
import com.aisip.OnO.backend.config.rabbitmq.producer.ProblemAnalysisProducer;
import com.aisip.OnO.backend.util.ai.AnalysisFailureReason;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 분석 요청 트랜잭션이 커밋된 뒤에 분석 메시지를 보낸다.
 *
 * 커밋 전에 보내면 컨슈머가 아직 커밋되지 않은 이전 상태를 읽는다. 예전에는 이 때문에
 * COMPLETED 를 다시 요청한 문제에서 컨슈머가 "이미 완료"로 보고 메시지를 버린 뒤
 * 요청 트랜잭션이 PROCESSING 을 커밋해, 결과가 채워진 채 PROCESSING 으로 굳은 행이 생겼다.
 *
 * 전송이 실패하면 요청은 이미 커밋됐으므로 되돌리지 않고 정리만 한다.
 * FAILED(DISPATCH) 로 바꾸고, 쓴 한도 1회를 돌려주고, 처리 중 표시를 지운다.
 * afterCommit 에서 새는 예외는 커밋을 마친 요청을 500 으로 만들기 때문에 여기서 모두 잡는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProblemAnalysisDispatcher {

    private final ProblemAnalysisProducer analysisProducer;
    private final ProblemAnalysisFailureService analysisFailureService;
    private final ProblemAnalysisDispatchGuard dispatchGuard;
    private final RateLimitService rateLimitService;

    /**
     * @param rateLimitKey 이 요청이 쓴 한도 키. 전송하지 못하면 이 키로 1회를 돌려준다
     */
    public void dispatchAfterCommit(Long problemId, Long userId, String rateLimitKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            send(problemId, userId, rateLimitKey);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                send(problemId, userId, rateLimitKey);
            }

            @Override
            public void afterCompletion(int status) {
                // 요청이 롤백되면 메시지는 나가지 않았고 PROCESSING 도 저장되지 않았다.
                // 표시와 한도를 남겨 두면 30분 동안 다시 요청하지 못하고 한도만 줄어든다.
                if (status == STATUS_ROLLED_BACK) {
                    dispatchGuard.release(problemId);
                    rateLimitService.refund(rateLimitKey, userId);
                }
            }
        });
    }

    private void send(Long problemId, Long userId, String rateLimitKey) {
        try {
            analysisProducer.sendAnalysisMessage(problemId);
        } catch (Exception e) {
            log.error("Analysis dispatch failed, marking FAILED - problemId: {}, userId: {}, error: {}",
                    problemId, userId, e.getMessage());
            handleDispatchFailure(problemId, userId, rateLimitKey);
        }
    }

    private void handleDispatchFailure(Long problemId, Long userId, String rateLimitKey) {
        try {
            // REQUIRES_NEW 라 이미 끝난 요청 트랜잭션과 상관없이 커밋된다. 처리 중 표시도 여기서 지운다.
            analysisFailureService.markFailed(problemId, AnalysisFailureReason.DISPATCH);
        } catch (Exception e) {
            log.error("Analysis dispatch failure could not be recorded - problemId: {}, error: {}",
                    problemId, e.getMessage());
            dispatchGuard.release(problemId);
        }
        rateLimitService.refund(rateLimitKey, userId);
    }
}
