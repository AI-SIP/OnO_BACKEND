package com.aisip.OnO.backend.service;

import com.aisip.OnO.backend.Dto.Problem.ProblemPracticeRegisterDto;
import com.aisip.OnO.backend.Dto.Problem.ProblemPracticeResponseDto;
import com.aisip.OnO.backend.entity.Problem.ProblemPractice;

public interface ProblemPracticeService {

    ProblemPractice createProblemPractice(Long userId, ProblemPracticeRegisterDto problemPracticeRegisterDto);

    void addProblemToPractice(Long practiceId, Long problemId);

    ProblemPracticeResponseDto getPracticeById(Long practiceId);

    void deletePractice(Long practiceId);

    void deleteProblemFromPractice(Long practiceId, Long problemId);

    void deleteProblemFromAllPractice(Long problemId);
}
