package com.aisip.OnO.backend.problem.dto;

import com.aisip.OnO.backend.common.validation.ValidationGroups;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

public record ProblemRegisterDto (
    // 등록(POST)에서는 비어 있고, 수정(PATCH /info, /path)에서만 필수다.
    @NotNull(groups = ValidationGroups.Update.class, message = "problemId 는 필수입니다.")
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
