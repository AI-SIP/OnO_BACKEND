package com.aisip.OnO.backend.problem.dto;

import java.util.List;

public record ProblemDeleteRequestDto(
        List<Long> deleteProblemIdList
) {
}
