package com.aisip.OnO.backend.repository;

import com.aisip.OnO.backend.entity.Problem.ProblemRepeat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProblemRepeatRepository extends JpaRepository<ProblemRepeat, Long> {
    Long countByProblemId(Long problemId);

    List<ProblemRepeat> findAllByProblemId(Long problemId);
}
