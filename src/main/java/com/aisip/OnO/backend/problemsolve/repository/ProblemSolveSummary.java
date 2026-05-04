package com.aisip.OnO.backend.problemsolve.repository;

import java.time.LocalDateTime;

public interface ProblemSolveSummary {
    Long getProblemId();

    Long getSolveCount();

    LocalDateTime getLastSolvedAt();
}
