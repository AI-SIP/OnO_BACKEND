package com.aisip.OnO.backend.problem.repository.problem;

import com.aisip.OnO.backend.problem.entity.Problem;

import java.util.List;

public interface ProblemRepositoryCustom {

    List<Problem> findAllByUserId(Long userId);

    List<Problem> findAllByFolderId(Long folderId);

    List<Problem> findAll();

    List<Problem> findAllProblemsByPracticeId(Long practiceId);
}
