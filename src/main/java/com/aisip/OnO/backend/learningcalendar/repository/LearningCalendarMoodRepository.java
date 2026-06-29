package com.aisip.OnO.backend.learningcalendar.repository;

import com.aisip.OnO.backend.learningcalendar.entity.LearningCalendarMood;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LearningCalendarMoodRepository extends JpaRepository<LearningCalendarMood, Long> {

    Optional<LearningCalendarMood> findByUserIdAndStudyDate(Long userId, LocalDate studyDate);

    List<LearningCalendarMood> findAllByUserIdAndStudyDateBetween(Long userId, LocalDate startDate, LocalDate endDate);
}
