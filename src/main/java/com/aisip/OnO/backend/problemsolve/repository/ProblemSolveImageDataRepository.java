package com.aisip.OnO.backend.problemsolve.repository;

import com.aisip.OnO.backend.problemsolve.entity.ProblemSolveImageData;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProblemSolveImageDataRepository extends JpaRepository<ProblemSolveImageData, Long> {

    List<ProblemSolveImageData> findAllByPracticeRecordId(Long practiceRecordId);

    void deleteByImageUrl(String imageUrl);
}