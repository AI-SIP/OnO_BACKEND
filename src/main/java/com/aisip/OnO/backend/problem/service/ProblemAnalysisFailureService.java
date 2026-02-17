package com.aisip.OnO.backend.problem.service;

import com.aisip.OnO.backend.problem.repository.ProblemAnalysisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProblemAnalysisFailureService {

    private final ProblemAnalysisRepository analysisRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long problemId, String errorMessage) {
        analysisRepository.findByProblemId(problemId).ifPresentOrElse(analysis -> {
            analysis.updateWithFailure(errorMessage);
            analysisRepository.save(analysis);
            log.info("Marked analysis as FAILED for problemId: {}", problemId);
        }, () -> log.warn("No analysis row found while marking FAILED. problemId: {}", problemId));
    }
}
