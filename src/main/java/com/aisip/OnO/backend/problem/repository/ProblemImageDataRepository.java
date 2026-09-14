package com.aisip.OnO.backend.problem.repository;

import com.aisip.OnO.backend.problem.entity.ProblemImageData;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProblemImageDataRepository extends JpaRepository<ProblemImageData, Long> {

    List<ProblemImageData> findAllByProblemId(Long problemId);

    Optional<ProblemImageData> findByImageUrl(String imageUrl);

    void deleteAllByProblemId(Long problemId);

    void deleteByImageUrl(String imageUrl);
}
