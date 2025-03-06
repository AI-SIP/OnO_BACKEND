package com.aisip.OnO.backend.problem.repository.problem;

import com.aisip.OnO.backend.problem.entity.ProblemImageData;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProblemImageDataRepository extends JpaRepository<ProblemImageData, Long> {

    void deleteAllByProblemId(Long problemId);

    void deleteByImageUrl(String imageUrl);
}
