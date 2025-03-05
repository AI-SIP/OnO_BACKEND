package com.aisip.OnO.backend.practicenote.repository;

import com.aisip.OnO.backend.practicenote.entity.PracticeNote;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PracticeNoteRepository extends JpaRepository<PracticeNote, Long>, PracticeNoteRepositoryCustom {

    @EntityGraph(attributePaths = {"user"})
    List<PracticeNote> findAllByUserId(Long userId);
}
