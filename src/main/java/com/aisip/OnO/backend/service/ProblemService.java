package com.aisip.OnO.backend.service;

import com.aisip.OnO.backend.Dto.Problem.ProblemRegisterDto;
import com.aisip.OnO.backend.Dto.Problem.ProblemResponseDto;

import java.util.List;

public interface ProblemService {

    public ProblemResponseDto saveProblem(Long userId, ProblemRegisterDto problemRegisterDto);

    List<ProblemResponseDto> findAllProblemsByUserId(Long userId);
}
