package com.aisip.OnO.backend.problem.dto;

import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.tag.dto.TagResponseDto;
import com.aisip.OnO.backend.tag.entity.ProblemTagMapping;
import lombok.AccessLevel;
import lombok.Builder;
import org.jetbrains.annotations.NotNull;

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

    List<ProblemImageDataResponseDto> imageUrlList,

    ProblemAnalysisResponseDto analysis,

    List<Long> tagIdList,

    List<TagResponseDto> tags
) {
    public static ProblemResponseDto from(@NotNull Problem problem) {

        List<ProblemImageDataResponseDto> problemImageDataList = Optional.ofNullable(problem.getProblemImageDataList())
                .orElse(List.of())
                .stream().map(ProblemImageDataResponseDto::from).toList();

        ProblemAnalysisResponseDto analysisDto = Optional.ofNullable(problem.getProblemAnalysis())
                .map(ProblemAnalysisResponseDto::from)
                .orElse(null);

        List<Long> tagIds = Optional.ofNullable(problem.getProblemTagMappingList())
                .orElse(List.of())
                .stream()
                .map(ProblemTagMapping::getTag)
                .map(tag -> tag.getId())
                .toList();

        List<TagResponseDto> tags = Optional.ofNullable(problem.getProblemTagMappingList())
                .orElse(List.of())
                .stream()
                .map(ProblemTagMapping::getTag)
                .map(TagResponseDto::from)
                .toList();

        return ProblemResponseDto.builder()
                .problemId(problem.getId())
                .folderId(problem.getFolder().getId())
                .memo(problem.getMemo())
                .reference(problem.getReference())
                .solvedAt(problem.getSolvedAt())
                .createdAt(problem.getCreatedAt())
                .updatedAt(problem.getUpdatedAt())
                .imageUrlList(problemImageDataList)
                .analysis(analysisDto)
                .tagIdList(tagIds)
                .tags(tags)
                .build();
    }
}
