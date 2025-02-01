package com.aisip.OnO.backend.repository.Practice;

import com.aisip.OnO.backend.entity.Problem.Practice;
import com.aisip.OnO.backend.entity.Problem.Problem;

import java.util.List;

public interface PracticeRepositoryCustom {

    List<Practice> findAllByProblemsContaining(Problem problem);

    List<Problem> findAllProblemsByPracticeId(Long practiceId);

    int countProblemsByPracticeId(Long practiceId);
}
