package com.aisip.OnO.backend.problem.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record AddProblemImageUrlsRequest(@NotNull List<ImageUrlItem> imageDataList) {

    public record ImageUrlItem(String imageUrl, String problemImageType) {}
}
