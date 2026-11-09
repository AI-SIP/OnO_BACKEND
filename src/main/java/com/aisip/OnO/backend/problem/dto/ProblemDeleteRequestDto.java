package com.aisip.OnO.backend.problem.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ProblemDeleteRequestDto(
        @NotNull(message = "deleteProblemIdList 는 필수입니다.")
        List<@NotNull(message = "deleteProblemIdList 에 빈 id 가 있습니다.") Long> deleteProblemIdList
) {
}
