package com.aisip.OnO.backend.problem.dto;

import java.util.List;

public record ProblemDeleteRequestDto (
        Long userId,
        List<Long> problemIdList,
        List<Long> folderIdList
) {}
