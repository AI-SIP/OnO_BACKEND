package com.aisip.OnO.backend.problem.repository;

import com.aisip.OnO.backend.problem.entity.Problem;

import java.util.List;
import java.util.Optional;

public interface ProblemRepositoryCustom {

    Optional<Problem> findProblemWithImageData(Long problemId);

    List<Problem> findAllByUserId(Long userId);

    List<Problem> findAllByFolderId(Long folderId);

    List<Problem> findAll();

    List<Problem> findAllProblemsByPracticeId(Long practiceId);

    /**
     * 커서 기반 폴더의 문제 조회
     * @param folderId 폴더 ID
     * @param cursor 마지막으로 조회한 문제 ID (null이면 처음부터)
     * @param size 조회할 개수
     * @return 문제 리스트 (size+1개 조회하여 hasNext 판단)
     */
    List<Problem> findProblemsByFolderWithCursor(Long folderId, Long cursor, int size);
}
