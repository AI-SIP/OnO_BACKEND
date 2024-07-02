package com.aisip.OnO.backend.service;

import com.aisip.OnO.backend.Dto.Problem.ProblemRegisterDto;
import com.aisip.OnO.backend.Dto.Problem.ProblemResponseDto;

public interface ProblemService {

    public ProblemResponseDto saveProblem(Long userId, ProblemRegisterDto problemRegisterDto);


}
