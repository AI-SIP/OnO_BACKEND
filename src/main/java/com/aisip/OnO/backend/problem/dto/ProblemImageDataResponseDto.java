package com.aisip.OnO.backend.problem.dto;

import com.aisip.OnO.backend.problem.entity.ProblemImageData;
import com.aisip.OnO.backend.problem.entity.ProblemImageType;
import lombok.AccessLevel;
import lombok.Builder;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDateTime;

@Builder(access = AccessLevel.PRIVATE)
public record ProblemImageDataResponseDto (

    String imageUrl,

    ProblemImageType problemImageType,

    LocalDateTime createdAt
) {
    public static ProblemImageDataResponseDto from(@NotNull ProblemImageData problemImageData) {
        return ProblemImageDataResponseDto.builder()
                .imageUrl(problemImageData.getImageUrl())
                .problemImageType(problemImageData.getProblemImageType())
                .createdAt(problemImageData.getCreatedAt())
                .build();
    }
}
