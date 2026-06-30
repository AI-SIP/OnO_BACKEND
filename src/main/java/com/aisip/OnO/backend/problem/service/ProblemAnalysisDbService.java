package com.aisip.OnO.backend.problem.service;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.problem.entity.AnalysisStatus;
import com.aisip.OnO.backend.problem.entity.ProblemAnalysis;
import com.aisip.OnO.backend.problem.entity.ProblemImageData;
import com.aisip.OnO.backend.problem.entity.ProblemImageType;
import com.aisip.OnO.backend.problem.exception.ProblemErrorCase;
import com.aisip.OnO.backend.problem.repository.ProblemAnalysisRepository;
import com.aisip.OnO.backend.problem.repository.ProblemImageDataRepository;
import com.aisip.OnO.backend.problem.repository.ProblemRepository;
import com.aisip.OnO.backend.util.ai.ProblemAnalysisResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * ProblemAnalysisService의 DB 접근 작업을 분리한 별도 빈.
 * analyzeProblemSync에서 self-invocation 없이 AOP 프록시를 거쳐 호출되어야 @Transactional이 정상 동작함.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProblemAnalysisDbService {

    private final ProblemRepository problemRepository;
    private final ProblemImageDataRepository problemImageDataRepository;
    private final ProblemAnalysisRepository analysisRepository;

    @Transactional(readOnly = true)
    public AnalysisPreparation fetchAnalysisPreparation(Long problemId) {
        problemRepository.findById(problemId)
                .orElseThrow(() -> new ApplicationException(ProblemErrorCase.PROBLEM_NOT_FOUND));

        List<String> imageUrls = problemImageDataRepository.findAllByProblemId(problemId)
                .stream()
                .filter(data -> data.getProblemImageType().equals(ProblemImageType.PROBLEM_IMAGE))
                .map(ProblemImageData::getImageUrl)
                .toList();

        ProblemAnalysis analysis = analysisRepository.findByProblemId(problemId)
                .orElseThrow(() -> new ApplicationException(ProblemErrorCase.PROBLEM_ANALYSIS_NOT_FOUND));

        return new AnalysisPreparation(imageUrls, analysis.getStatus().equals(AnalysisStatus.COMPLETED));
    }

    @Transactional
    public void saveAnalysisSuccess(Long problemId, ProblemAnalysisResult result, String keyPointsJson) {
        ProblemAnalysis analysis = analysisRepository.findByProblemId(problemId)
                .orElseThrow(() -> new ApplicationException(ProblemErrorCase.PROBLEM_ANALYSIS_NOT_FOUND));
        // 다른 스레드가 먼저 완료한 경우 덮어쓰기 방지
        if (analysis.getStatus().equals(AnalysisStatus.COMPLETED)) {
            log.info("Analysis already COMPLETED by another thread, skipping save. problemId: {}", problemId);
            return;
        }
        analysis.updateWithSuccess(
                result.getSubject(),
                result.getProblemType(),
                keyPointsJson,
                result.getSolution(),
                result.getCommonMistakes(),
                result.getStudyTips()
        );
        analysisRepository.save(analysis);
    }

    public record AnalysisPreparation(List<String> imageUrls, boolean alreadyCompleted) {}
}
