package com.aisip.OnO.backend.service;

import com.aisip.OnO.backend.Dto.Problem.ProblemPractice.ProblemPracticeRegisterDto;
import com.aisip.OnO.backend.Dto.Problem.ProblemPractice.ProblemPracticeResponseDto;
import com.aisip.OnO.backend.entity.Problem.ProblemPractice;

import java.util.List;

public interface ProblemPracticeService {

    ProblemPractice createProblemPractice(Long userId, ProblemPracticeRegisterDto problemPracticeRegisterDto);

    void addProblemToPractice(Long practiceId, Long problemId);

    ProblemPractice findPracticeEntity(Long practiceId);

    List<ProblemPracticeResponseDto> findAllPracticeByUser(Long userId);

    ProblemPractice updatePractice(Long practiceId, ProblemPracticeRegisterDto problemPracticeRegisterDto);

    void deletePractice(Long practiceId);

    void deleteProblemFromPractice(Long practiceId, Long problemId);

    void deleteProblemFromAllPractice(Long problemId);
}
