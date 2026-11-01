package com.aisip.OnO.backend.problem.service;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.problem.entity.AnalysisStatus;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.problem.entity.ProblemAnalysis;
import com.aisip.OnO.backend.problem.entity.ProblemImageData;
import com.aisip.OnO.backend.problem.entity.ProblemImageType;
import com.aisip.OnO.backend.problem.exception.ProblemErrorCase;
import com.aisip.OnO.backend.problem.repository.ProblemAnalysisRepository;
import com.aisip.OnO.backend.problem.repository.ProblemImageDataRepository;
import com.aisip.OnO.backend.problem.repository.ProblemRepository;
import com.aisip.OnO.backend.util.ai.AnalysisInput;
import com.aisip.OnO.backend.util.ai.ProblemAnalysisResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
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

    /**
     * 분석에 넘길 입력(문제 이미지, 해설 이미지, 제목, 메모)과 완료 여부를 짧은 읽기 트랜잭션으로 모은다.
     * OpenAI 호출은 이 트랜잭션 밖에서 하므로 엔티티가 아니라 값만 넘긴다.
     * 이미지는 등록 순서(id 순)로 넘겨 "[문제 이미지 1/2]" 라벨이 앱에서 올린 순서와 같게 한다.
     */
    @Transactional(readOnly = true)
    public AnalysisPreparation fetchAnalysisPreparation(Long problemId) {
        Problem problem = problemRepository.findById(problemId)
                .orElseThrow(() -> new ApplicationException(ProblemErrorCase.PROBLEM_NOT_FOUND));

        List<ProblemImageData> images = problemImageDataRepository.findAllByProblemId(problemId)
                .stream()
                .sorted(Comparator.comparing(ProblemImageData::getId))
                .toList();

        ProblemAnalysis analysis = analysisRepository.findByProblemId(problemId)
                .orElseThrow(() -> new ApplicationException(ProblemErrorCase.PROBLEM_ANALYSIS_NOT_FOUND));

        AnalysisInput input = new AnalysisInput(
                imageUrlsOf(images, ProblemImageType.PROBLEM_IMAGE),
                imageUrlsOf(images, ProblemImageType.ANSWER_IMAGE),
                problem.getReference(),
                problem.getMemo());
        return new AnalysisPreparation(input, analysis.getStatus().equals(AnalysisStatus.COMPLETED));
    }

    private static List<String> imageUrlsOf(List<ProblemImageData> images, ProblemImageType type) {
        return images.stream()
                .filter(data -> data.getProblemImageType() == type)
                .map(ProblemImageData::getImageUrl)
                .toList();
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

    public record AnalysisPreparation(AnalysisInput input, boolean alreadyCompleted) {}
}
