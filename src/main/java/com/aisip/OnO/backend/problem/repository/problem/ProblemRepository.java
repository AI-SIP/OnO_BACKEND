package com.aisip.OnO.backend.problem.repository.problem;

import com.aisip.OnO.backend.problem.entity.Problem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProblemRepository extends JpaRepository<Problem, Long>, ProblemRepositoryCustom {

    Long countByUserId(Long userId);
}
