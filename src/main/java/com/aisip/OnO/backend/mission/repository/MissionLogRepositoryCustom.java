package com.aisip.OnO.backend.mission.repository;

public interface MissionLogRepositoryCustom {
    boolean alreadyWriteProblemsTodayMoreThan3(Long userId);

    boolean alreadyPracticeProblem(Long problemId);

    boolean alreadyPracticeNote(Long practiceNoteId);

    boolean alreadyLogin(Long userId);

    Long getPointSumToday(Long userId);
}
