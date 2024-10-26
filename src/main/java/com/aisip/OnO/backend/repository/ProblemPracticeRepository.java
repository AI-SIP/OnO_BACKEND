package com.aisip.OnO.backend.repository;

import com.aisip.OnO.backend.entity.Problem.Problem;
import com.aisip.OnO.backend.entity.Problem.ProblemPractice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProblemPracticeRepository extends JpaRepository<ProblemPractice, Long> {
    List<ProblemPractice> findAllByProblemsContaining(Problem problem);
}
