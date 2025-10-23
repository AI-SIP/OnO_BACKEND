package com.aisip.OnO.backend.problem.service;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.problem.dto.ProblemAnalysisResponseDto;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.problem.entity.ProblemAnalysis;
import com.aisip.OnO.backend.problem.exception.ProblemErrorCase;
import com.aisip.OnO.backend.problem.repository.ProblemAnalysisRepository;
import com.aisip.OnO.backend.problem.repository.ProblemRepository;
import com.aisip.OnO.backend.util.ai.OpenAIClient;
import com.aisip.OnO.backend.util.ai.ProblemAnalysisResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ProblemAnalysisService {

    private final ProblemAnalysisRepository analysisRepository;
    private final ProblemRepository problemRepository;
    private final OpenAIClient openAIClient;
    private final ObjectMapper objectMapper;

    /**
     * 기존 분석 결과 삭제
     */
    public void deleteAnalysis(Long problemId) {
        try {
            analysisRepository.findByProblemId(problemId)
                    .ifPresent(analysisRepository::delete);
            log.info("Deleted existing analysis for problemId: {}", problemId);
        } catch (Exception e) {
            log.error("Error deleting analysis for problemId: {}", problemId, e);
        }
    }

    /**
     * 이미지가 없을 때 분석 스킵 상태로 저장
     */
    public void createSkippedAnalysis(Long problemId) {
        try {
            Problem problem = problemRepository.findById(problemId)
                    .orElseThrow(() -> new ApplicationException(ProblemErrorCase.PROBLEM_NOT_FOUND));

            if (!analysisRepository.existsByProblemId(problemId)) {
                ProblemAnalysis skipped = ProblemAnalysis.createSkipped(problem);
                problem.updateProblemAnalysis(skipped);
                analysisRepository.save(skipped);
                log.info("Created skipped analysis for problemId: {}", problemId);
            }
        } catch (Exception e) {
            log.error("Error creating skipped analysis for problemId: {}", problemId, e);
        }
    }

    /**
     * PROCESSING 상태의 분석 엔티티를 미리 생성 (비동기 작업 전)
     */
    public void createPendingAnalysis(Long problemId) {
        Problem problem = problemRepository.findById(problemId)
                .orElseThrow(() -> new ApplicationException(ProblemErrorCase.PROBLEM_NOT_FOUND));

        if (!analysisRepository.existsByProblemId(problemId)) {
            ProblemAnalysis pending = ProblemAnalysis.createProcessing(problem);
            problem.updateProblemAnalysis(pending);
            analysisRepository.save(pending);
            log.info("Created pending analysis for problemId: {}", problemId);
        }
    }

    /**
     * 비동기로 문제 이미지를 분석합니다.
     */
    @Async
    public void analyzeProblemAsync(Long problemId, List<String> imageUrls) {
        log.info("Starting async analysis for problemId: {}, imageUrls: {}", problemId, imageUrls);

        try {
            // 1. Problem 조회
            Problem problem = problemRepository.findById(problemId)
                    .orElseThrow(() -> new ApplicationException(ProblemErrorCase.PROBLEM_NOT_FOUND));

            // 2. 이미 분석 중이거나 완료된 경우 스킵
            if (analysisRepository.existsByProblemId(problemId)) {
                log.info("Analysis already exists for problemId: {}", problemId);
                return;
            }

            // 3. 분석 상태 생성 (PROCESSING)
            ProblemAnalysis analysis = ProblemAnalysis.createProcessing(problem);
            problem.updateProblemAnalysis(analysis);
            analysisRepository.save(analysis);

            // 4. OpenAI API 호출 (여러 이미지를 하나의 문제로 분석)
            ProblemAnalysisResult result = openAIClient.analyzeImages(imageUrls);

            // 5. keyPoints를 JSON 문자열로 변환
            String keyPointsJson = objectMapper.writeValueAsString(result.getKeyPoints());

            // 6. 분석 결과 업데이트 (COMPLETED)
            analysis.updateWithSuccess(
                    result.getSubject(),
                    result.getProblemType(),
                    keyPointsJson,
                    result.getSolution(),
                    result.getCommonMistakes(),
                    result.getStudyTips()
            );

            analysisRepository.save(analysis);
            log.info("Analysis completed successfully for problemId: {}", problemId);

        } catch (Exception e) {
            log.error("Error during analysis for problemId: {}", problemId, e);
            handleAnalysisError(problemId, e);
        }
    }

    /**
     * [TEST ONLY] 이미지 URL로 직접 AI 분석 테스트
     * 실제 서비스에서는 사용하지 않음 - 테스트 후 삭제 예정
     */
    public ProblemAnalysisResponseDto testAnalyzeImage(String imageUrl) {
        try {
            log.info("Testing image analysis for URL: {}", imageUrl);

            // AI 분석 실행
            ProblemAnalysisResult result = openAIClient.analyzeImage(imageUrl);

            // keyPoints를 JSON 문자열로 변환
            String keyPointsJson = objectMapper.writeValueAsString(result.getKeyPoints());

            // DTO로 변환하여 반환 (임시 ID 사용)
            return ProblemAnalysisResponseDto.builder()
                    .id(0L)
                    .problemId(0L)
                    .subject(result.getSubject())
                    .problemType(result.getProblemType())
                    .keyPoints(result.getKeyPoints())
                    .solution(result.getSolution())
                    .commonMistakes(result.getCommonMistakes())
                    .studyTips(result.getStudyTips())
                    .status("COMPLETED")
                    .errorMessage(null)
                    .build();

        } catch (Exception e) {
            log.error("Error during test analysis", e);
            return ProblemAnalysisResponseDto.builder()
                    .id(0L)
                    .problemId(0L)
                    .status("FAILED")
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    /**
     * 분석 결과를 조회합니다.
     */
    @Transactional(readOnly = true)
    public ProblemAnalysisResponseDto getAnalysis(Long problemId, Long userId) {
        // 1. Problem 조회 및 권한 확인
        Problem problem = problemRepository.findById(problemId)
                .orElseThrow(() -> new ApplicationException(ProblemErrorCase.PROBLEM_NOT_FOUND));

        if (!problem.getUserId().equals(userId)) {
            throw new ApplicationException(ProblemErrorCase.PROBLEM_USER_UNMATCHED);
        }

        // 2. Analysis 조회
        ProblemAnalysis analysis = analysisRepository.findByProblemId(problemId)
                .orElseThrow(() -> new ApplicationException(ProblemErrorCase.PROBLEM_ANALYSIS_NOT_FOUND));

        return ProblemAnalysisResponseDto.from(analysis);
    }

    /**
     * 에러 처리
     */
    private void handleAnalysisError(Long problemId, Exception e) {
        try {
            ProblemAnalysis analysis = analysisRepository.findByProblemId(problemId)
                    .orElse(null);

            if (analysis != null) {
                analysis.updateWithFailure(e.getMessage());
                analysisRepository.save(analysis);
            }
        } catch (Exception ex) {
            log.error("Error handling analysis error for problemId: {}", problemId, ex);
        }
    }
}
