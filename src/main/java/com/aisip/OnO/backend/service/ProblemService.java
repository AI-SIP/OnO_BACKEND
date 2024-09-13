package com.aisip.OnO.backend.service;

import com.aisip.OnO.backend.Dto.Problem.ProblemRegisterDto;
import com.aisip.OnO.backend.Dto.Problem.ProblemResponseDto;

import java.util.List;

public interface ProblemService {

    ProblemResponseDto findProblemByUserId(Long userId, Long problemId);

    Long findAllProblemCountByUserId(Long userId);

    List<ProblemResponseDto> findAllProblemsByUserId(Long userId);

    List<ProblemResponseDto> findAllProblemsByFolderId(Long folderId);

    boolean saveProblem(Long userId, ProblemRegisterDto problemRegisterDto);

    boolean updateProblem(Long userId, ProblemRegisterDto problemRegisterDto);

    void deleteProblem(Long userId, Long problemId);

    void deleteUserProblems(Long userId);
}
