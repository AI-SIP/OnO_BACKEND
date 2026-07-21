package com.aisip.OnO.backend.problem.event;

import java.time.LocalDateTime;
import java.util.List;

public record ProblemCreatedEvent(Long userId, List<ProblemData> problems) {
    public record ProblemData(Long problemId, String memo, String reference, LocalDateTime createdAt) {}
}
