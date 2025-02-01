package com.aisip.OnO.backend.repository.Repeat;

import com.aisip.OnO.backend.entity.Problem.ProblemRepeat;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProblemRepeatRepository extends JpaRepository<ProblemRepeat, Long> {
    Long countByProblemId(Long problemId);

    @EntityGraph(attributePaths = {"problem"})
    List<ProblemRepeat> findAllByProblemId(Long problemId);
}
