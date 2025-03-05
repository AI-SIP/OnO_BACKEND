package com.aisip.OnO.backend.practicenote.repository;

import com.aisip.OnO.backend.practicenote.entity.PracticeNote;
import com.aisip.OnO.backend.problem.entity.Problem;

import java.util.List;

public interface PracticeNoteRepositoryCustom {

    List<PracticeNote> findAllByProblemsContaining(Problem problem);

    List<Problem> findAllProblemsByPracticeId(Long practiceId);

    int countProblemsByPracticeId(Long practiceId);
}
