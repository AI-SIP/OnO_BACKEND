package com.aisip.OnO.backend.problem.dto;

import com.aisip.OnO.backend.problem.entity.ProblemImageType;

public record ProblemImageDataRegisterDto(

        Long problemId,

        String imageUrl,

        ProblemImageType problemImageType
) { }
