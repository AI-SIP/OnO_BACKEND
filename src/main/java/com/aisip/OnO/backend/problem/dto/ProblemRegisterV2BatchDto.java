package com.aisip.OnO.backend.problem.dto;

import java.util.List;

public record ProblemRegisterV2BatchDto(
        List<ProblemRegisterV2Dto> problems
) {
}
