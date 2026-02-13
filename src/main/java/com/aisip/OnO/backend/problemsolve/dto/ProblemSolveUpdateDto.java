package com.aisip.OnO.backend.problemsolve.dto;

import com.aisip.OnO.backend.problemsolve.entity.AnswerStatus;
import com.aisip.OnO.backend.problemsolve.entity.ImprovementType;

import java.util.List;

public record ProblemSolveUpdateDto(
        Long problemSolveId,
        AnswerStatus answerStatus,
        String reflection,
        List<ImprovementType> improvements,
        Integer timeSpentSeconds
) {
}