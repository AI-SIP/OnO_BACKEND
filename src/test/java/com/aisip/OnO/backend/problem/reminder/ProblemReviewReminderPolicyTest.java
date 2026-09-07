package com.aisip.OnO.backend.problem.reminder;

import com.aisip.OnO.backend.util.fcm.dto.NotificationRequestDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProblemReviewReminderPolicyTest {

    private ProblemReviewReminderPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new ProblemReviewReminderPolicy();
    }

    // ──────────────────────── getIntervals ─────────────────────────

    @Test
    @DisplayName("intervals는 [1, 3, 7, 14, 30] 5개여야 한다")
    void getIntervals_returnsExpectedList() {
        assertThat(policy.getIntervals()).containsExactly(1, 3, 7, 14, 30);
    }

    // ──────────────────────── calculateScheduledAt ─────────────────

    @Test
    @DisplayName("일반 시간대 작성 시 각 interval별 예약시각은 createdAt + N일과 동일하다")
    void calculateScheduledAt_normalTime_allIntervals() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 18, 22, 35, 0);
        List<Integer> intervals = policy.getIntervals(); // [1, 3, 7, 14, 30]

        for (int intervalDays : intervals) {
            LocalDateTime result = policy.calculateScheduledAt(createdAt, intervalDays);
            assertThat(result)
                    .as("interval=%d 일 때 예약시각이 createdAt + %d일이어야 함", intervalDays, intervalDays)
                    .isEqualTo(createdAt.plusDays(intervalDays));
        }
    }

    @Test
    @DisplayName("야간(00:05) 작성 후 D+1 예약시각은 다음날 06:00로 조정된다")
    void calculateScheduledAt_nightTime_adjustsToMorning() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 18, 0, 5, 0);
        LocalDateTime result = policy.calculateScheduledAt(createdAt, 1);
        assertThat(result).isEqualTo(LocalDateTime.of(2026, 7, 19, 6, 0, 0));
    }

    @Test
    @DisplayName("야간 경계: 05:59 작성 → D+1 06:00 조정")
    void calculateScheduledAt_justBeforeNightEnd_adjusts() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 18, 5, 59, 0);
        LocalDateTime result = policy.calculateScheduledAt(createdAt, 1);
        assertThat(result).isEqualTo(LocalDateTime.of(2026, 7, 19, 6, 0, 0));
    }

    @Test
    @DisplayName("야간 경계: 06:00 정각 작성 → 조정 없이 D+1 06:00 그대로")
    void calculateScheduledAt_exactlyNightEnd_noAdjustment() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 18, 6, 0, 0);
        LocalDateTime result = policy.calculateScheduledAt(createdAt, 1);
        assertThat(result).isEqualTo(LocalDateTime.of(2026, 7, 19, 6, 0, 0));
    }

    @Test
    @DisplayName("야간이 아닌 23:50 작성 → D+1 23:50 그대로 유지")
    void calculateScheduledAt_lateEvening_noAdjustment() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 18, 23, 50, 0);
        LocalDateTime result = policy.calculateScheduledAt(createdAt, 1);
        assertThat(result).isEqualTo(LocalDateTime.of(2026, 7, 19, 23, 50, 0));
    }

    // ──────────────────────── buildNotification ────────────────────

    @Test
    @DisplayName("memo/reference 모두 null이면 fallback body가 사용된다")
    void buildNotification_noContent_usesFallbackBody() {
        ProblemReviewReminder reminder = ProblemReviewReminder.create(
                1L, 100L, null, null, 1, 1, LocalDateTime.now().plusDays(1)
        );

        NotificationRequestDto dto = policy.buildNotification(reminder);

        assertThat(dto.body()).isEqualTo("이전에 남긴 오답노트를 다시 풀어보세요");
    }

    @Test
    @DisplayName("memo/reference가 공백뿐이어도 fallback body가 사용된다")
    void buildNotification_blankContent_usesFallbackBody() {
        ProblemReviewReminder reminder = ProblemReviewReminder.create(
                1L, 100L, "   ", "\t", 1, 1, LocalDateTime.now().plusDays(1)
        );

        NotificationRequestDto dto = policy.buildNotification(reminder);

        assertThat(dto.body())
                .as("공백만 담긴 스냅샷으로 '그때 남긴 오답' 문구를 쓰면 보여줄 내용이 없다")
                .isEqualTo("이전에 남긴 오답노트를 다시 풀어보세요");
    }

    @Test
    @DisplayName("memo가 있으면 default body가 사용된다")
    void buildNotification_memoPresent_usesDefaultBody() {
        ProblemReviewReminder reminder = ProblemReviewReminder.create(
                1L, 100L, "삼각형 넓이 공식", null, 1, 1, LocalDateTime.now().plusDays(1)
        );

        NotificationRequestDto dto = policy.buildNotification(reminder);

        assertThat(dto.body()).isEqualTo("그때 남긴 오답, 다시 풀어볼 시간이에요");
    }

    @Test
    @DisplayName("reference만 있어도 default body가 사용된다")
    void buildNotification_referenceOnly_usesDefaultBody() {
        ProblemReviewReminder reminder = ProblemReviewReminder.create(
                1L, 100L, null, "수학 교재 p.45", 2, 3, LocalDateTime.now().plusDays(3)
        );

        NotificationRequestDto dto = policy.buildNotification(reminder);

        assertThat(dto.body()).isEqualTo("그때 남긴 오답, 다시 풀어볼 시간이에요");
    }

    @Test
    @DisplayName("FCM 알림 제목은 '오답노트 복습'이다")
    void buildNotification_titleIsCorrect() {
        ProblemReviewReminder reminder = ProblemReviewReminder.create(
                1L, 100L, "memo", null, 1, 1, LocalDateTime.now().plusDays(1)
        );

        NotificationRequestDto dto = policy.buildNotification(reminder);

        assertThat(dto.title()).isEqualTo("오답노트 복습");
    }

    @Test
    @DisplayName("FCM data payload에 type, problemId, sequence, intervalDays가 포함된다")
    void buildNotification_dataPayloadContainsRequiredKeys() {
        long problemId = 42L;
        int sequence = 3;
        int intervalDays = 7;
        ProblemReviewReminder reminder = ProblemReviewReminder.create(
                1L, problemId, "memo", null, sequence, intervalDays, LocalDateTime.now().plusDays(intervalDays)
        );

        NotificationRequestDto dto = policy.buildNotification(reminder);
        Map<String, String> data = dto.data();

        assertThat(data).containsEntry("type", "problem_review_reminder");
        assertThat(data).containsEntry("problemId", String.valueOf(problemId));
        assertThat(data).containsEntry("sequence", String.valueOf(sequence));
        assertThat(data).containsEntry("intervalDays", String.valueOf(intervalDays));
    }
}
