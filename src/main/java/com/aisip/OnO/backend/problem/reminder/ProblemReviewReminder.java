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

    /**
     * 스냅샷 컬럼은 varchar(255)(V21)인데 memo 는 1000자까지 들어온다(V24).
     * 알림 문구용 사본이라 잘라 담는다. 넘치면 insert 자체가 truncation 으로 실패한다.
     */
    private static final int SNAPSHOT_MAX_LENGTH = 255;

    public static String truncateSnapshot(String value) {
        if (value == null || value.length() <= SNAPSHOT_MAX_LENGTH) {
            return value;
        }
        return value.substring(0, SNAPSHOT_MAX_LENGTH);
    }

    public static ProblemReviewReminder create(
            Long userId, Long problemId, String memo, String reference,
            int sequence, int intervalDays, LocalDateTime scheduledAt) {
        return ProblemReviewReminder.builder()
                .userId(userId)
                .problemId(problemId)
                .problemMemoSnapshot(truncateSnapshot(memo))
                .problemReferenceSnapshot(truncateSnapshot(reference))
                .sequence(sequence)
                .intervalDays(intervalDays)
                .scheduledAt(scheduledAt)
                .status(ProblemReviewReminderStatus.SCHEDULED)
                .retryCount(0)
                .build();
    }
}
