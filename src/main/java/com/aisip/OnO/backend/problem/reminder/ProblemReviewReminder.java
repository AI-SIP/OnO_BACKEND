package com.aisip.OnO.backend.problem.reminder;

import com.aisip.OnO.backend.common.entity.BaseEntity;
import com.aisip.OnO.backend.problem.entity.Problem;
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

    /** Problem.memo 를 그대로 복사하므로 길이 제약도 같이 맞춰야 한다. */
    @Column(length = Problem.MEMO_MAX_LENGTH)
    private String problemMemoSnapshot;

    @Column(length = Problem.REFERENCE_MAX_LENGTH)
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
     * 스냅샷은 잘라 담지 않는다.
     *
     * <p>memo 스냅샷 컬럼은 V25 에서 varchar(1000) 으로 넓혀 {@code Problem.MEMO_MAX_LENGTH} 와 같고,
     * reference 스냅샷은 varchar(255) 로 {@code Problem.REFERENCE_MAX_LENGTH} 와 같다.
     * 입력 길이는 저장 전에 ProblemService 에서 걸러지므로 여기서 자르면 알림 문구만 손실된다.
     */
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
