package com.aisip.OnO.backend.problem.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record UpdateProblemImageDataRequest(
        @NotNull Long problemId,
        @NotNull List<ImageItem> imageDataDtoList
) {
    public record ImageItem(String imageUrl, String problemImageType) {}
}
