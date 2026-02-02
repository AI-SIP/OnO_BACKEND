package com.aisip.OnO.backend.mission.repository;

import com.aisip.OnO.backend.user.entity.User;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface MissionLogRepositoryCustom {
    boolean alreadyWriteProblemsTodayMoreThan3(Long userId);

    boolean alreadyPracticeProblem(Long problemId);

    boolean alreadyPracticeNote(Long practiceNoteId);

    boolean alreadyLogin(Long userId);

    Long getPointSumToday(Long userId);

    Map<LocalDate, Long> getDailyActiveUsersCount(int days);

    List<User> getActiveUsersByDate(LocalDate date);
}
