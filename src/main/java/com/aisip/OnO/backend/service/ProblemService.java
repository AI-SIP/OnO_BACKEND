package com.aisip.OnO.backend.service;

import com.aisip.OnO.backend.Dto.Problem.ProblemRegisterDto;
import com.aisip.OnO.backend.Dto.Problem.ProblemResponseDto;

import java.util.List;

public interface ProblemService {

    ProblemResponseDto findProblemByUserId(Long userId, Long problemId);

    List<ProblemResponseDto> findAllProblemsByUserId(Long userId);
    boolean saveProblem(Long userId, ProblemRegisterDto problemRegisterDto);

    //ProblemResponseDto updateProblem(Long userId, Long problemId, ProblemRegisterDto problemRegisterDto);

    void deleteProblem(Long userId, Long problemId);
}
