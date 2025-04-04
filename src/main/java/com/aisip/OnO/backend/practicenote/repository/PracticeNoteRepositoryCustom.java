package com.aisip.OnO.backend.practicenote.repository;

import com.aisip.OnO.backend.practicenote.entity.PracticeNote;

import java.util.List;
import java.util.Set;

public interface PracticeNoteRepositoryCustom {

    boolean checkProblemAlreadyMatchingWithPractice(Long practiceNoteId, Long problemId);

    PracticeNote findPracticeNoteWithDetails(Long practiceId);

    Set<Long> findProblemIdListByPracticeNoteId(Long practiceNoteId);

    void deleteProblemFromPractice(Long practiceId, Long problemId);

    void deleteProblemsFromAllPractice(List<Long> deleteProblemIdList);
}
