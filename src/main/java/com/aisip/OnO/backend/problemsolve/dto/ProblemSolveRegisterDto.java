package com.aisip.OnO.backend.problemsolve.dto;

import com.aisip.OnO.backend.problemsolve.entity.AnswerStatus;
import com.aisip.OnO.backend.problemsolve.entity.ImprovementType;

import java.time.LocalDateTime;
import java.util.List;

public record ProblemSolveRegisterDto(
        Long problemId,
        LocalDateTime practicedAt,
        AnswerStatus answerStatus,
        String reflection,
        List<ImprovementType> improvements,
        Integer timeSpentSeconds
) {
}