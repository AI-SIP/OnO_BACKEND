package com.aisip.OnO.backend.problem.dto;

import com.aisip.OnO.backend.problem.entity.Problem;
import lombok.AccessLevel;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Builder(access = AccessLevel.PRIVATE)
public record ProblemResponseDto (

    Long problemId,

    Long folderId,

    String memo,

    String reference,

    LocalDateTime solvedAt,

    LocalDateTime createdAt,

    LocalDateTime updatedAt,

    List<ProblemImageDataResponseDto> imageUrlList
) {
    public static ProblemResponseDto from(Problem problem) {

        List<ProblemImageDataResponseDto> problemImageDataList = Optional.ofNullable(problem.getProblemImageDataList())
                .orElse(List.of())
                .stream().map(ProblemImageDataResponseDto::from).toList();

        return ProblemResponseDto.builder()
                .problemId(problem.getId())
                .folderId(problem.getFolder().getId())
                .memo(problem.getMemo())
                .reference(problem.getReference())
                .solvedAt(problem.getSolvedAt())
                .createdAt(problem.getCreatedAt())
                .updatedAt(problem.getUpdatedAt())
                .imageUrlList(problemImageDataList)
                .build();
    }
}

