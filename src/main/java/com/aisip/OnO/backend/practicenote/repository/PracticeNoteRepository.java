package com.aisip.OnO.backend.practicenote.repository;

import com.aisip.OnO.backend.practicenote.entity.PracticeNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface PracticeNoteRepository extends JpaRepository<PracticeNote, Long>, PracticeNoteRepositoryCustom {

    List<PracticeNote> findAllByUserId(Long userId);

    long countByCreatedAtBetween(LocalDateTime startDateTime, LocalDateTime endDateTime);

    @Query("""
            SELECT FUNCTION('DATE', p.createdAt), COUNT(p)
            FROM PracticeNote p
            WHERE p.createdAt BETWEEN :startDateTime AND :endDateTime
            GROUP BY FUNCTION('DATE', p.createdAt)
            """)
    List<Object[]> countDailyPracticeNotes(
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime
    );

    @Query("""
            SELECT m.practiceNote.id, COUNT(m.problem.id)
            FROM ProblemPracticeNoteMapping m
            WHERE m.practiceNote.id IN :practiceNoteIds
            GROUP BY m.practiceNote.id
            """)
    List<Object[]> countProblemsByPracticeNoteIds(@Param("practiceNoteIds") List<Long> practiceNoteIds);

    @Query("SELECT p.id FROM PracticeNote p WHERE p.userId = :userId")
    List<Long> findAllPracticeIdsByUserId(@Param("userId") Long userId);
}
