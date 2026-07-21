package com.aisip.OnO.backend.problem.reminder;

import com.aisip.OnO.backend.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "problem_review_reminder", indexes = {
        @Index(name = "idx_problem_review_reminder_due", columnList = "status, scheduled_at"),
        @Index(name = "idx_problem_review_reminder_user_due", columnList = "user_id, status, scheduled_at"),
        @Index(name = "idx_problem_review_reminder_problem", columnList = "problem_id, status"),
}, uniqueConstraints = {
        @UniqueConstraint(name = "uq_problem_review_reminder_seq", columnNames = {"problem_id", "sequence"})
})
public class ProblemReviewReminder extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long problemId;

    private String problemMemoSnapshot;

    private String problemReferenceSnapshot;

    @Column(nullable = false)
    private int sequence;

    @Column(nullable = false)
    private int intervalDays;

    @Column(nullable = false)
    private LocalDateTime scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ProblemReviewReminderStatus status;

    private LocalDateTime sentAt;

    @Column(length = 500)
    private String lastErrorMessage;

    @Column(nullable = false)
    @Builder.Default
    private int retryCount = 0;

    public static ProblemReviewReminder create(
            Long userId, Long problemId, String memo, String reference,
            int sequence, int intervalDays, LocalDateTime scheduledAt) {
        return ProblemReviewReminder.builder()
                .userId(userId)
                .problemId(problemId)
                .problemMemoSnapshot(memo)
                .problemReferenceSnapshot(reference)
                .sequence(sequence)
                .intervalDays(intervalDays)
                .scheduledAt(scheduledAt)
                .status(ProblemReviewReminderStatus.SCHEDULED)
                .retryCount(0)
                .build();
    }
}
