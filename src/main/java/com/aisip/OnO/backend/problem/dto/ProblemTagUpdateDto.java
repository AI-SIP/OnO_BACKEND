package com.aisip.OnO.backend.problem.dto;

import java.util.List;

public record ProblemTagUpdateDto(
        List<Long> addTagIds,
        List<Long> removeTagIds
) {
}
