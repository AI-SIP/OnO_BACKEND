package com.aisip.OnO.backend.util.fileupload.dto;

public record PresignedUrlResponse(
        String presignedUrl,
        String fileUrl
) {}
