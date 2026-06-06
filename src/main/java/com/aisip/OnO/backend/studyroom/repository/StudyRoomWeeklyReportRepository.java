package com.aisip.OnO.backend.studyroom.repository;

import com.aisip.OnO.backend.studyroom.entity.StudyRoomWeeklyReport;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface StudyRoomWeeklyReportRepository extends JpaRepository<StudyRoomWeeklyReport, Long> {

    boolean existsByRoomIdAndWeekStart(Long roomId, LocalDate weekStart);

    Optional<StudyRoomWeeklyReport> findByIdAndRoomId(Long id, Long roomId);

    List<StudyRoomWeeklyReport> findAllByRoomIdOrderByWeekStartDesc(Long roomId, Pageable pageable);
}
