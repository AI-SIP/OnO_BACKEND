package com.aisip.OnO.backend.converter;

import com.aisip.OnO.backend.Dto.Problem.ProblemResponseDto;
import com.aisip.OnO.backend.entity.Problem;

public class ProblemConverter {

    public static ProblemResponseDto convertToResponseDto(Problem problem) {
        if (problem == null) {
            return null;
        }

        ProblemResponseDto dto = new ProblemResponseDto();
        dto.setProblemId(problem.getId());
        dto.setImageUrl(problem.getImageUrl());
        dto.setProcessImageUrl(problem.getProcessImageUrl());
        dto.setAnswerImageUrl(problem.getAnswerImageUrl());
        dto.setSolveImageUrl(problem.getSolveImageUrl());
        dto.setMemo(problem.getMemo());
        dto.setReference(problem.getReference());
        dto.setSolvedAt(problem.getSolvedAt());

        return dto;
    }
}
