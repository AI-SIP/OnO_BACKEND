package com.aisip.OnO.backend.admin.dto;

import java.time.LocalDateTime;

public record AdminProblemResponseDto(
        Long problemId,
        Long folderId,
        String memo,
        String reference,
        String analysisStatus,
        LocalDateTime solvedAt,
        LocalDateTime createdAt
) {
}
