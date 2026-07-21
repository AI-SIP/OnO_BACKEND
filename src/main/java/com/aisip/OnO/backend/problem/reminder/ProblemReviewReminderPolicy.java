package com.aisip.OnO.backend.problem.reminder;

import com.aisip.OnO.backend.util.fcm.dto.NotificationRequestDto;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

@Component
public class ProblemReviewReminderPolicy {

    private static final List<Integer> INTERVALS = List.of(1, 3, 7, 14, 30);
    private static final LocalTime NIGHT_END = LocalTime.of(6, 0);
    private static final String NOTIFICATION_TITLE = "오답노트 복습";
    private static final String NOTIFICATION_BODY_DEFAULT = "그때 남긴 오답, 다시 풀어볼 시간이에요";
    private static final String NOTIFICATION_BODY_FALLBACK = "이전에 남긴 오답노트를 다시 풀어보세요";

    public List<Integer> getIntervals() {
        return INTERVALS;
    }

    public LocalDateTime calculateScheduledAt(LocalDateTime createdAt, int intervalDays) {
        LocalDateTime candidate = createdAt.plusDays(intervalDays);
        if (candidate.toLocalTime().isBefore(NIGHT_END)) {
            return candidate.toLocalDate().atTime(NIGHT_END);
        }
        return candidate;
    }

    public NotificationRequestDto buildNotification(ProblemReviewReminder reminder) {
        boolean hasContent = hasContent(reminder.getProblemMemoSnapshot())
                || hasContent(reminder.getProblemReferenceSnapshot());
        String body = hasContent ? NOTIFICATION_BODY_DEFAULT : NOTIFICATION_BODY_FALLBACK;

        Map<String, String> data = Map.of(
                "type", "problem_review_reminder",
                "problemId", String.valueOf(reminder.getProblemId()),
                "sequence", String.valueOf(reminder.getSequence()),
                "intervalDays", String.valueOf(reminder.getIntervalDays())
        );

        return new NotificationRequestDto("", NOTIFICATION_TITLE, body, data);
    }

    private boolean hasContent(String value) {
        return value != null && !value.isBlank();
    }
}
