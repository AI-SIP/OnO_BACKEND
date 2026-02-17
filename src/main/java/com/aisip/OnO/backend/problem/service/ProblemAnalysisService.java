package com.aisip.OnO.backend.problem.service;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.problem.dto.ProblemAnalysisResponseDto;
import com.aisip.OnO.backend.problem.entity.AnalysisStatus;
import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.problem.entity.ProblemAnalysis;
import com.aisip.OnO.backend.problem.entity.ProblemImageData;
import com.aisip.OnO.backend.problem.entity.ProblemImageType;
import com.aisip.OnO.backend.problem.exception.ProblemErrorCase;
import com.aisip.OnO.backend.problem.repository.ProblemAnalysisRepository;
import com.aisip.OnO.backend.problem.repository.ProblemImageDataRepository;
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
    private final ProblemImageDataRepository problemImageDataRepository;
    private final ProblemAnalysisFailureService problemAnalysisFailureService;

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
     * 분석 상태를 PROCESSING으로 업데이트 (이미지 있을 때)
     */
    public void updateToProcessing(Long problemId) {
        ProblemAnalysis analysis = analysisRepository.findByProblemId(problemId)
                .orElseThrow(() -> new ApplicationException(ProblemErrorCase.PROBLEM_NOT_FOUND));

        analysis.updateToProcessing();
        analysisRepository.save(analysis);
        log.info("Updated analysis to PROCESSING for problemId: {}", problemId);
    }

    /**
     * 분석 상태를 NO_IMAGE로 업데이트 (이미지 없을 때)
     */
    public void updateToNoImage(Long problemId) {
        ProblemAnalysis analysis = analysisRepository.findByProblemId(problemId)
                .orElseThrow(() -> new ApplicationException(ProblemErrorCase.PROBLEM_NOT_FOUND));

        analysis.updateToNoImage();
        analysisRepository.save(analysis);
        log.info("Updated analysis to NO_IMAGE for problemId: {}", problemId);
    }

    /**
     * 동기적으로 문제 이미지를 분석합니다 (RabbitMQ Consumer에서 호출)
     * - 동시성 문제 해결: 이미 생성된 PROCESSING 엔티티만 조회하여 업데이트
     */
    @Transactional
    public void analyzeProblemSync(Long problemId) {
        log.info("Starting sync analysis for problemId: {}", problemId);

        try {
            // 1. Problem 조회
            Problem problem = problemRepository.findById(problemId)
                    .orElseThrow(() -> new ApplicationException(ProblemErrorCase.PROBLEM_NOT_FOUND));

            // 2. 문제 이미지 url 조회
            List<String> problemImageUrls = problemImageDataRepository.findAllByProblemId(problemId)
                    .stream()
                    .filter(data -> data.getProblemImageType().equals(ProblemImageType.PROBLEM_IMAGE))
                    .map(ProblemImageData::getImageUrl)
                    .toList();

            // 3. 기존 분석 조회 (동시성 문제 해결: 새로 생성하지 않고 조회만)
            ProblemAnalysis analysis = analysisRepository.findByProblemId(problemId)
                    .orElseThrow(() -> new ApplicationException(ProblemErrorCase.PROBLEM_ANALYSIS_NOT_FOUND));

            // 4. 이미 완료된 경우 스킵
            if (analysis.getStatus().equals(AnalysisStatus.COMPLETED)) {
                log.info("Analysis already completed for problemId: {}", problemId);
                return;
            }

            // 5. OpenAI API 호출 (여러 이미지를 하나의 문제로 분석)
            ProblemAnalysisResult result = openAIClient.analyzeImages(problemImageUrls);

            // 6. keyPoints를 JSON 문자열로 변환
            String keyPointsJson;
            try {
                keyPointsJson = objectMapper.writeValueAsString(result.getKeyPoints());
            } catch (Exception jsonException) {
                log.error("JSON 변환 실패 for problemId: {}", problemId, jsonException);
                throw new RuntimeException("JSON 변환 실패", jsonException);
            }

            // 7. 분석 결과 업데이트 (COMPLETED)
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

        } catch (ApplicationException e) {
            // 삭제된 문제는 재시도 가치가 없어 상위 Consumer에서 비재시도 처리한다.
            if (e.getErrorCase() == ProblemErrorCase.PROBLEM_NOT_FOUND) {
                log.warn("Skipping analysis because problem was deleted or missing. problemId: {}", problemId);
                throw e;
            }

            log.error("Application error during sync analysis for problemId: {}", problemId, e);
            handleAnalysisError(problemId, e);
            throw e;
        } catch (Exception e) {
            log.error("Error during sync analysis for problemId: {}", problemId, e);
            handleAnalysisError(problemId, e);
            throw e; // RabbitMQ 재시도를 위해 예외 다시 던짐
        }
    }

    /**
     * 비동기로 문제 이미지를 분석합니다 (기존 @Async 방식, deprecated)
     * @deprecated RabbitMQ 방식(analyzeProblemSync)을 사용하세요
     */
    @Deprecated
    @Async
    public void analyzeProblemAsync(Long problemId) {
        log.info("Starting async analysis for problemId: {}, imageUrls: {}", problemId);

        try {
            // Problem 조회
            Problem problem = problemRepository.findById(problemId)
                    .orElseThrow(() -> new ApplicationException(ProblemErrorCase.PROBLEM_NOT_FOUND));

            // 문제 이미지 url 조회
            List<String> problemImageUrls = problemImageDataRepository.findAllByProblemId(problemId)
                    .stream()
                    // 1. Enum 값이 PROBLEM_IMAGE인 데이터만 필터링
                    .filter(data -> data.getProblemImageType() == ProblemImageType.PROBLEM_IMAGE)
                    // 2. 해당 객체에서 imageUrl 필드만 추출
                    .map(ProblemImageData::getImageUrl)
                    // 3. 리스트로 변환
                    .toList(); // Java 16 이상 기준 (이하는 .collect(Collectors.toList()))

            // 기존 분석 조회 또는 새로 생성
            ProblemAnalysis analysis = analysisRepository.findByProblemId(problemId)
                    .orElseGet(() -> {
                        ProblemAnalysis newAnalysis = ProblemAnalysis.createProcessing(problem);
                        problem.updateProblemAnalysis(newAnalysis);
                        return analysisRepository.save(newAnalysis);
                    });

            // 3. 이미 완료된 경우 스킵
            if (analysis.getStatus() == AnalysisStatus.COMPLETED) {
                log.info("Analysis already completed for problemId: {}", problemId);
                return;
            }

            // 4. OpenAI API 호출 (여러 이미지를 하나의 문제로 분석)
            ProblemAnalysisResult result = openAIClient.analyzeImages(problemImageUrls);

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

        // 2. Analysis 조회 (없으면 null 필드로 반환)
        return analysisRepository.findByProblemId(problemId)
                .map(ProblemAnalysisResponseDto::from)
                .orElse(ProblemAnalysisResponseDto.builder()
                        .id(null)
                        .problemId(problemId)
                        .subject(null)
                        .problemType(null)
                        .keyPoints(null)
                        .solution(null)
                        .commonMistakes(null)
                        .studyTips(null)
                        .status(AnalysisStatus.NOT_STARTED.toString())
                        .errorMessage(null)
                        .build());
    }

    /**
     * 에러 처리
     */
    private void handleAnalysisError(Long problemId, Exception e) {
        try {
            problemAnalysisFailureService.markFailed(problemId, e.getMessage());
        } catch (Exception ex) {
            log.error("Error handling analysis error for problemId: {}", problemId, ex);
        }
    }
}
