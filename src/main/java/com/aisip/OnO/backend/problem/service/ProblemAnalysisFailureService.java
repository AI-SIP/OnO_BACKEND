package com.aisip.OnO.backend.problem.service;

import com.aisip.OnO.backend.problem.entity.AnalysisStatus;
import com.aisip.OnO.backend.problem.repository.ProblemAnalysisRepository;
import com.aisip.OnO.backend.util.ai.AnalysisFailureReason;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 분석의 최종 실패를 기록한다.
 *
 * 여기 오는 것은 더 이상 재시도하지 않는 실패뿐이다(재시도하지 않는 예외, DLQ, 큐 전송 실패).
 * 재시도가 남은 중간 실패는 ProblemAnalysisService 가 여기로 보내지 않는다.
 * 그래서 실패 메트릭도 여기서 한 번만 센다. 시도마다 세면 같은 메시지 하나가 최대 3번 잡혀
 * 사용자가 실제로 실패를 본 횟수와 맞지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProblemAnalysisFailureService {

    static final String FAILURE_METRIC = "ono.ai.analysis.failures";

    private final ProblemAnalysisRepository analysisRepository;
    private final ProblemAnalysisDispatchGuard dispatchGuard;
    private final MeterRegistry meterRegistry;

    /**
     * 앱은 FAILED 일 때 error_message 를 그대로 보여 주므로 사유별 사용자 문구를 저장한다.
     * 예외 원문은 호출한 쪽에서 로그로만 남긴다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long problemId, AnalysisFailureReason reason) {
        AnalysisFailureReason failureReason = reason == null ? AnalysisFailureReason.TRANSIENT : reason;

        analysisRepository.findByProblemId(problemId).ifPresentOrElse(analysis -> {
            // 겹쳐 나간 다른 메시지가 이미 분석을 끝냈으면 그 결과를 FAILED 로 가리지 않는다.
            if (analysis.getStatus() == AnalysisStatus.COMPLETED) {
                log.info("Analysis already COMPLETED, skipping FAILED mark. problemId: {}, reason: {}",
                        problemId, failureReason.tag());
                return;
            }
            analysis.updateWithFailure(failureReason.userMessage());
            analysisRepository.save(analysis);
            countFailure(failureReason);
            log.info("Marked analysis as FAILED for problemId: {}, reason: {}", problemId, failureReason.tag());
        }, () -> log.warn("No analysis row found while marking FAILED. problemId: {}", problemId));

        // 분석이 끝났으니 바로 다시 요청할 수 있게 한다.
        dispatchGuard.releaseAfterCommit(problemId);
    }

    private void countFailure(AnalysisFailureReason reason) {
        Counter.builder(FAILURE_METRIC)
                .description("AI problem analysis final failures")
                .tag("reason", reason.tag())
                .register(meterRegistry)
                .increment();
    }
}
