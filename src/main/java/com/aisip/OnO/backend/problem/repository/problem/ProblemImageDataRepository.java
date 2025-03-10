package com.aisip.OnO.backend.problem.repository.problem;

import com.aisip.OnO.backend.problem.entity.ProblemImageData;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProblemImageDataRepository extends JpaRepository<ProblemImageData, Long> {

    List<ProblemImageData> findAllByProblemId(Long problemId);

    void deleteAllByProblemId(Long problemId);

    void deleteByImageUrl(String imageUrl);
}
