package com.aisip.OnO.backend.practicenote.repository;

import com.aisip.OnO.backend.practicenote.entity.PracticeNote;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface PracticeNoteRepositoryCustom {

    boolean checkProblemAlreadyMatchingWithPractice(Long practiceNoteId, Long problemId);

    Optional<PracticeNote> findPracticeNoteWithDetails(Long practiceId);

    List<PracticeNote> findAllUserPracticeNotesWithDetails(Long userId);

    List<Long> findProblemIdListByPracticeNoteId(Long practiceNoteId);

    void deleteProblemFromPractice(Long practiceId, Long problemId);

    void deleteProblemFromAllPractice(Long problemId);

    void deleteProblemsFromAllPractice(List<Long> deleteProblemIdList);
}
