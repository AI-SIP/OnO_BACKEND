package com.aisip.OnO.backend.problem.repository;

import com.aisip.OnO.backend.problem.entity.ProblemAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProblemAnalysisRepository extends JpaRepository<ProblemAnalysis, Long> {

    Optional<ProblemAnalysis> findByProblemId(Long problemId);

    boolean existsByProblemId(Long problemId);
}
