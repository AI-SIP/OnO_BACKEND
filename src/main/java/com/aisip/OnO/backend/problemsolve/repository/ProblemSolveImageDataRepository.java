package com.aisip.OnO.backend.problemsolve.repository;

import com.aisip.OnO.backend.problemsolve.entity.ProblemSolveImageData;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProblemSolveImageDataRepository extends JpaRepository<ProblemSolveImageData, Long> {

    List<ProblemSolveImageData> findAllByProblemSolveId(Long problemSolveId);

    Optional<ProblemSolveImageData> findByImageUrl(String imageUrl);

    void deleteByImageUrl(String imageUrl);
}
