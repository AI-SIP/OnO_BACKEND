package com.aisip.OnO.backend.repository.Practice;

import com.aisip.OnO.backend.entity.Problem.Problem;
import com.aisip.OnO.backend.entity.Problem.ProblemPractice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProblemPracticeRepository extends JpaRepository<ProblemPractice, Long> {
    List<ProblemPractice> findAllByProblemsContaining(Problem problem);

    List<ProblemPractice> findAllByUserId(Long userId);

    @Query("SELECT COUNT(p) FROM ProblemPractice pp JOIN pp.problems p WHERE pp.id = :practiceId")
    int countProblemsByPracticeId(@Param("practiceId") Long practiceId);
}
