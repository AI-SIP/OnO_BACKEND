package com.aisip.OnO.backend.problem.repository;

import com.aisip.OnO.backend.problem.entity.ProblemImageData;
import com.aisip.OnO.backend.problem.entity.ProblemImageType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProblemImageRepository extends JpaRepository<ProblemImageData, Long> {
    List<ProblemImageData> findByProblemId(Long problemId);

    Optional<ProblemImageData> findByImageUrl(String imageUrl);
    Optional<ProblemImageData> findByProblemIdAndImageType(Long problemId, ProblemImageType problemImageType);

    Long countAllByImageType(ProblemImageType problemImageType);
}
