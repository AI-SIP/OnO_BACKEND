package com.aisip.OnO.backend.problem.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ProblemRegisterV2Dto(
        Long problemId,
        String memo,
        String reference,
        Long folderId,
        LocalDateTime solvedAt,
        List<String> problemImageUrls,
        List<String> answerImageUrls,
        List<Long> tagIds
) {
    public ProblemRegisterV2Dto(
            Long problemId,
            String memo,
            String reference,
            Long folderId,
            LocalDateTime solvedAt,
            List<String> problemImageUrls,
            List<String> answerImageUrls
    ) {
        this(problemId, memo, reference, folderId, solvedAt, problemImageUrls, answerImageUrls, null);
    }
}
