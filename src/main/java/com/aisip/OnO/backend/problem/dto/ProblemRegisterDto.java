package com.aisip.OnO.backend.problem.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ProblemRegisterDto (
    Long problemId,

    String memo,

    String reference,

    Long folderId,

    LocalDateTime solvedAt,

    List<Long> tagIds
) {
    public ProblemRegisterDto(
            Long problemId,
            String memo,
            String reference,
            Long folderId,
            LocalDateTime solvedAt
    ) {
        this(problemId, memo, reference, folderId, solvedAt, null);
    }
}
