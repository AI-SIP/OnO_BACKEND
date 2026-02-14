package com.aisip.OnO.backend.problemsolve.dto;

import com.aisip.OnO.backend.problemsolve.entity.AnswerStatus;
import com.aisip.OnO.backend.problemsolve.entity.ImprovementType;
import com.aisip.OnO.backend.problemsolve.entity.ProblemSolve;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Builder
public record ProblemSolveResponseDto(
        Long problemSolveId,
        Long problemId,
        Long userId,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        LocalDateTime practicedAt,
        AnswerStatus answerStatus,
        String reflection,
        List<ImprovementType> improvements,
        Integer timeSpentSeconds,
        Boolean migratedFromLegacy,
        List<String> imageUrls,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        LocalDateTime createdAt,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        LocalDateTime updatedAt
) {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static ProblemSolveResponseDto from(ProblemSolve problemSolve) {
        List<ImprovementType> improvementList = Collections.emptyList();
        if (problemSolve.getImprovements() != null && !problemSolve.getImprovements().isEmpty()) {
            try {
                improvementList = objectMapper.readValue(
                        problemSolve.getImprovements(),
                        new TypeReference<List<ImprovementType>>() {}
                );
            } catch (Exception e) {
                // JSON 파싱 실패 시 빈 리스트 반환
                improvementList = Collections.emptyList();
            }
        }

        return ProblemSolveResponseDto.builder()
                .problemSolveId(problemSolve.getId())
                .problemId(problemSolve.getProblem().getId())
                .userId(problemSolve.getUserId())
                .practicedAt(problemSolve.getPracticedAt())
                .answerStatus(problemSolve.getAnswerStatus())
                .reflection(problemSolve.getReflection())
                .improvements(improvementList)
                .timeSpentSeconds(problemSolve.getTimeSpentSeconds())
                .migratedFromLegacy(problemSolve.getMigratedFromLegacy())
                .imageUrls(problemSolve.getImages().stream()
                        .sorted((i1, i2) -> i1.getImageOrder().compareTo(i2.getImageOrder()))
                        .map(image -> image.getImageUrl())
                        .collect(Collectors.toList()))
                .createdAt(problemSolve.getCreatedAt())
                .updatedAt(problemSolve.getUpdatedAt())
                .build();
    }
}