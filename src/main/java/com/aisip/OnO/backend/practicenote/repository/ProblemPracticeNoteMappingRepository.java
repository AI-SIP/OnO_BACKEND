package com.aisip.OnO.backend.practicenote.repository;

import com.aisip.OnO.backend.practicenote.entity.ProblemPracticeNoteMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProblemPracticeNoteMappingRepository extends JpaRepository<ProblemPracticeNoteMapping, Long> {
    Optional<ProblemPracticeNoteMapping> findProblemPracticeNoteMappingByProblemIdAndPracticeNoteId(Long problemId, Long practiceNoteId);
}
