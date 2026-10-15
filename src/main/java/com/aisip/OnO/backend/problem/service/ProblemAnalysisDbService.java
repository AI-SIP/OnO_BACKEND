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
    private final ProblemAnalysisDispatchGuard dispatchGuard;
    private final ProblemAnalysisQuotaRefunder quotaRefunder;

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
        // 분석이 끝났으니 처리 중 표시를 지운다. COMPLETED 는 다시 요청해도 건너뛰므로 순서는 중요하지 않다.
        dispatchGuard.releaseAfterCommit(problemId);
    }

    /**
     * 메시지를 보낸 뒤 처리 전에 문제 이미지가 모두 지워진 경우. 분석할 것이 없으니 실패가 아니라 요청 때와 같은 NO_IMAGE 로 둔다.
     * AI 를 쓰지 않았으므로 PROCESSING 에서 바뀔 때만 한도 1회를 돌려준다(겹친 메시지가 두 번 돌려주지 않게).
     */
    @Transactional
    public void finishWithoutImage(Long problemId) {
        ProblemAnalysis analysis = analysisRepository.findByProblemId(problemId)
                .orElseThrow(() -> new ApplicationException(ProblemErrorCase.PROBLEM_ANALYSIS_NOT_FOUND));
        if (analysis.getStatus().equals(AnalysisStatus.COMPLETED)) {
            log.info("Analysis already COMPLETED, skipping NO_IMAGE. problemId: {}", problemId);
            dispatchGuard.releaseAfterCommit(problemId);
            return;
        }
        boolean wasProcessing = analysis.getStatus().equals(AnalysisStatus.PROCESSING);
        analysis.updateToNoImage();
        analysisRepository.save(analysis);
        if (wasProcessing) {
            quotaRefunder.refundAfterCommit(analysis.getProblem().getUserId());
        }
        dispatchGuard.releaseAfterCommit(problemId);
        log.info("No problem image left at analysis time, marked NO_IMAGE. problemId: {}", problemId);
    }

    public record AnalysisPreparation(List<String> imageUrls, boolean alreadyCompleted) {}
}
