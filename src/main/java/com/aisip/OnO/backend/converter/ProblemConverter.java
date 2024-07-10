package com.aisip.OnO.backend.converter;

import com.aisip.OnO.backend.Dto.Problem.ProblemResponseDto;
import com.aisip.OnO.backend.entity.Image.ImageData;
import com.aisip.OnO.backend.entity.Problem;

import java.util.List;

public class ProblemConverter {

    public static ProblemResponseDto convertToResponseDto(Problem problem, List<ImageData> images) {
        if (problem == null) {
            return null;
        }

        ProblemResponseDto dto = new ProblemResponseDto();
        dto.setProblemId(problem.getId());
        dto.setMemo(problem.getMemo());
        dto.setReference(problem.getReference());
        dto.setSolvedAt(problem.getSolvedAt());

        // 이미지 데이터를 DTO에 설정
        for (ImageData image : images) {
            switch (image.getImageType()) {
                case PROBLEM_IMAGE:
                    dto.setProblemImageUrl(image.getImageUrl());
                    break;
                case PROCESS_IMAGE:
                    dto.setProcessImageUrl(image.getImageUrl());
                    break;
                case ANSWER_IMAGE:
                    dto.setAnswerImageUrl(image.getImageUrl());
                    break;
                case SOLVE_IMAGE:
                    dto.setSolveImageUrl(image.getImageUrl());
                    break;
            }
        }

        return dto;
    }
}
