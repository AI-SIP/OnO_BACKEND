package com.aisip.OnO.backend.problem.repository;

import com.aisip.OnO.backend.problem.entity.ProblemSolve;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProblemSolveRepository extends JpaRepository<ProblemSolve, Long> {
    Long countByProblemId(Long problemId);

    @EntityGraph(attributePaths = {"problem"})
    List<ProblemSolve> findAllByProblemId(Long problemId);
}
