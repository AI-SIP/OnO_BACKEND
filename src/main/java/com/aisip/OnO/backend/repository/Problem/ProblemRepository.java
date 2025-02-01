package com.aisip.OnO.backend.repository.Problem;

import com.aisip.OnO.backend.entity.Problem.Problem;
import com.aisip.OnO.backend.entity.Problem.TemplateType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProblemRepository extends JpaRepository<Problem, Long> {

    Long countByUserId(Long userId);

    @EntityGraph(attributePaths = {"user"})
    List<Problem> findAllByUserId(Long userId);

    @EntityGraph(attributePaths = {"folder"})
    List<Problem> findAllByFolderId(Long folderId);

    List<Problem>findAllByUserIdAndFolderIsNull(Long userId);

    Long countAllByTemplateTypeIsNull();

    Long countAllByTemplateType(TemplateType type);
}
