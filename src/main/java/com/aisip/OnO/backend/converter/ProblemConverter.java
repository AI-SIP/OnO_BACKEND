package com.aisip.OnO.backend.converter;

import com.aisip.OnO.backend.Dto.Problem.ProblemRegisterDto;
import com.aisip.OnO.backend.Dto.Problem.ProblemResponseDto;
import com.aisip.OnO.backend.entity.Image.ImageData;
import com.aisip.OnO.backend.entity.Problem;

import java.util.List;

public class ProblemConverter {

    public static ProblemResponseDto convertToResponseDto(Problem problem, List<ImageData> images) {
        if (problem == null) {
            return null;
        }

        ProblemResponseDto problemResponseDto = ProblemResponseDto.builder()
                .problemId(problem.getId())
                .memo(problem.getMemo())
                .reference(problem.getReference())
                .solvedAt(problem.getSolvedAt())
                .build();
        
        // 이미지 데이터를 DTO에 설정
        for (ImageData image : images) {
            switch (image.getImageType()) {
                case PROBLEM_IMAGE:
                    problemResponseDto.setProblemImageUrl(image.getImageUrl());
                    break;
                case PROCESS_IMAGE:
                    problemResponseDto.setProcessImageUrl(image.getImageUrl());
                    break;
                case ANSWER_IMAGE:
                    problemResponseDto.setAnswerImageUrl(image.getImageUrl());
                    break;
                case SOLVE_IMAGE:
                    problemResponseDto.setSolveImageUrl(image.getImageUrl());
                    break;
            }
        }

        return problemResponseDto;
    }
}
