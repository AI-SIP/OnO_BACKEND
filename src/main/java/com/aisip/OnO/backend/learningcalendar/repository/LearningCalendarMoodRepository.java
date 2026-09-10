package com.aisip.OnO.backend.learningcalendar.repository;

import com.aisip.OnO.backend.learningcalendar.entity.LearningCalendarMood;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface LearningCalendarMoodRepository extends JpaRepository<LearningCalendarMood, Long> {

    Optional<LearningCalendarMood> findByUserIdAndStudyDate(Long userId, LocalDate studyDate);

    List<LearningCalendarMood> findAllByUserIdAndStudyDateBetween(Long userId, LocalDate startDate, LocalDate endDate);

    /**
     * 오늘 이 사용자가 기분을 남긴 적이 있는지. 어느 날짜에 남겼는지가 아니라 <b>언제 남겼는지</b>를 본다.
     *
     * <p>미션 진행도를 하루 한 번만 올리기 위한 판정이다. 자세한 이유는
     * {@code LearningCalendarService.updateMood} 주석에 있다.
     */
    boolean existsByUserIdAndCreatedAtBetween(Long userId, LocalDateTime start, LocalDateTime end);
}
