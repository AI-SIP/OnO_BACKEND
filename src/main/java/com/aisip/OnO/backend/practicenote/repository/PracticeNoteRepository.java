package com.aisip.OnO.backend.practicenote.repository;

import com.aisip.OnO.backend.practicenote.entity.PracticeNote;
import org.springframework.data.jpa.repository.JpaRepository;

import javax.swing.text.html.Option;
import java.util.List;
import java.util.Optional;

public interface PracticeNoteRepository extends JpaRepository<PracticeNote, Long>, PracticeNoteRepositoryCustom {

    List<PracticeNote> findAllByUserId(Long userId);
}
