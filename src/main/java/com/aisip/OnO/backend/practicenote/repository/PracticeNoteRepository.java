package com.aisip.OnO.backend.practicenote.repository;

import com.aisip.OnO.backend.practicenote.entity.PracticeNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PracticeNoteRepository extends JpaRepository<PracticeNote, Long>, PracticeNoteRepositoryCustom {

    List<PracticeNote> findAllByUserId(Long userId);

    @Query("SELECT p.id FROM PracticeNote p WHERE p.userId = :userId")
    List<Long> findAllPracticeIdsByUserId(@Param("userId") Long userId);
}
