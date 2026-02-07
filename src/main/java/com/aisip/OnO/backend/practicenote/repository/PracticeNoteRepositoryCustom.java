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

    /**
     * 커서 기반 복습노트 썸네일 조회
     * @param userId 유저 ID
     * @param cursor 마지막으로 조회한 복습노트 ID (null이면 처음부터)
     * @param size 조회할 개수
     * @return 복습노트 리스트 (size+1개 조회하여 hasNext 판단)
     */
    List<PracticeNote> findPracticeNotesByUserWithCursor(Long userId, Long cursor, int size);
}
