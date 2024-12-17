package com.aisip.OnO.backend.service;

import com.aisip.OnO.backend.Dto.Problem.ProblemPractice.ProblemPracticeRegisterDto;
import com.aisip.OnO.backend.Dto.Problem.ProblemPractice.ProblemPracticeResponseDto;
import com.aisip.OnO.backend.entity.Problem.ProblemPractice;

import java.util.List;

public interface ProblemPracticeService {

    ProblemPractice getPracticeEntity(Long practiceId);

    ProblemPracticeResponseDto createPractice(Long userId, ProblemPracticeRegisterDto problemPracticeRegisterDto);

    void addProblemToPractice(Long practiceId, Long problemId);

    ProblemPracticeResponseDto findPractice(Long practiceId);

    List<ProblemPracticeResponseDto> findAllPracticesByUser(Long userId);

    boolean addPracticeCount(Long practiceId);

    boolean updatePractice(ProblemPracticeRegisterDto problemPracticeRegisterDto);

    void deletePractice(Long practiceId);

    void deletePractices(List<Long> practiceIds);

    void deleteAllPracticesByUser(Long userId);

    void removeProblemFromPractice(Long practiceId, Long problemId);

    void deleteProblemFromAllPractice(Long problemId);
}
