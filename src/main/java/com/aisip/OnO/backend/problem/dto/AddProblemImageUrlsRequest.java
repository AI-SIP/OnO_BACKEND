package com.aisip.OnO.backend.problem.dto;

import java.util.List;

public record AddProblemImageUrlsRequest(List<ImageUrlItem> imageDataList) {

    public record ImageUrlItem(String imageUrl, String problemImageType) {}
}
