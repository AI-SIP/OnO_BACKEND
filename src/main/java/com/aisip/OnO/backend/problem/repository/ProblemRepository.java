package com.aisip.OnO.backend.problem.repository;

import com.aisip.OnO.backend.problem.entity.Problem;
import com.aisip.OnO.backend.problem.entity.ProblemTemplateType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProblemRepository extends JpaRepository<Problem, Long> {

    Long countByUserId(Long userId);

    @EntityGraph(attributePaths = {"user"})
    List<Problem> findAllByUserId(Long userId);

    @EntityGraph(attributePaths = {"folder"})
    List<Problem> findAllByFolderId(Long folderId);

    Long countAllByTemplateTypeIsNull();

    Long countAllByTemplateType(ProblemTemplateType type);
}
