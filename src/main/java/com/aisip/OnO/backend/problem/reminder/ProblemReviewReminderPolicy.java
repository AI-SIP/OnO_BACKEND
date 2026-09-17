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
    private static final LocalTime SEND_WINDOW_START = LocalTime.of(9, 0);
    private static final LocalTime SEND_WINDOW_END = LocalTime.of(21, 0);
    private static final String NOTIFICATION_TITLE = "오답노트 복습";
    private static final String NOTIFICATION_BODY_DEFAULT = "그때 남긴 오답, 다시 풀어볼 시간이에요";
    private static final String NOTIFICATION_BODY_FALLBACK = "이전에 남긴 오답노트를 다시 풀어보세요";

    public List<Integer> getIntervals() {
        return INTERVALS;
    }

    /**
     * 예약 시각을 보내도 되는 시간대 안으로 밀어 넣는다.
     *
     * <p>이른 새벽이면 같은 날 아침으로 당기고, 밤이면 다음 날 아침으로 미룬다.
     * 예전에는 경계가 {@code NIGHT_END}(06:00) 하나뿐이라 새벽만 막았고,
     * 22시에 등록하면 D+N 22시에 그대로 나갔다.
     */
    public LocalDateTime calculateScheduledAt(LocalDateTime createdAt, int intervalDays) {
        LocalDateTime candidate = createdAt.plusDays(intervalDays);
        LocalTime candidateTime = candidate.toLocalTime();

        if (candidateTime.isBefore(SEND_WINDOW_START)) {
            return candidate.toLocalDate().atTime(SEND_WINDOW_START);
        }
        if (!candidateTime.isBefore(SEND_WINDOW_END)) {
            return candidate.toLocalDate().plusDays(1).atTime(SEND_WINDOW_START);
        }
        return candidate;
    }

    /**
     * 지금 보내도 되는 시간인가.
     *
     * <p>예약 시각을 창 안으로 맞춰 두어도 밀린 예약은 창 밖에서 조건을 만족한다.
     * 특히 자정에 "오늘 보낸 것이 있는가" 판정이 리셋되면서 밀린 예약이 한꺼번에
     * due 가 되므로, 예약을 만들 때만이 아니라 보낼 때도 시각을 확인해야 한다.
     */
    public boolean isWithinSendWindow(LocalDateTime now) {
        LocalTime time = now.toLocalTime();
        return !time.isBefore(SEND_WINDOW_START) && time.isBefore(SEND_WINDOW_END);
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
