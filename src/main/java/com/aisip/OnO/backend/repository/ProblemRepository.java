package com.aisip.OnO.backend.repository;

import com.aisip.OnO.backend.entity.Problem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProblemRepository extends JpaRepository<Problem, Long> {
    List<Problem> findAllByUserId(Long userId);

    List<Problem> findAllByFolderId(Long folderId);

    List<Problem>findAllByUserIdAndFolderIsNull(Long userId);
}
