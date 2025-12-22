package com.aisip.OnO.backend.problem.dto;

import com.aisip.OnO.backend.problem.entity.ProblemAnalysis;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;

import java.util.List;

@Builder
public record ProblemAnalysisResponseDto(
        Long id,
        Long problemId,
        String subject,
        String problemType,
        List<String> keyPoints,
        String solution,
        String commonMistakes,
        String studyTips,
        String status,
        String errorMessage
) {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static ProblemAnalysisResponseDto from(ProblemAnalysis analysis) {
        // keyPoints는 JSON 배열 문자열이므로 파싱
        List<String> keyPointsList = null;
        if (analysis.getKeyPoints() != null) {
            try {
                keyPointsList = objectMapper.readValue(
                        analysis.getKeyPoints(),
                        new TypeReference<List<String>>() {}
                );
            } catch (Exception e) {
                keyPointsList = List.of();
            }
        }

        return ProblemAnalysisResponseDto.builder()
                .id(analysis.getId())
                .problemId(analysis.getProblem().getId())
                .subject(analysis.getSubject())
                .problemType(analysis.getProblemType())
                .keyPoints(keyPointsList)
                .solution(analysis.getSolution())
                .commonMistakes(analysis.getCommonMistakes())
                .studyTips(analysis.getStudyTips())
                .status(analysis.getStatus().name())
                .errorMessage(analysis.getErrorMessage())
                .build();
    }
}
