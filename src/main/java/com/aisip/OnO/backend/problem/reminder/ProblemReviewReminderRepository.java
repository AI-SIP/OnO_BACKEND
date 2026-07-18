package com.aisip.OnO.backend.problem.reminder;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ProblemReviewReminderRepository extends JpaRepository<ProblemReviewReminder, Long> {

    boolean existsByProblemIdAndSequence(Long problemId, int sequence);

    @Query("SELECT r FROM ProblemReviewReminder r WHERE r.status = :status AND r.scheduledAt <= :now AND r.deletedAt IS NULL ORDER BY r.userId ASC, r.scheduledAt ASC")
    List<ProblemReviewReminder> findDueReminders(
            @Param("status") ProblemReviewReminderStatus status,
            @Param("now") LocalDateTime now
    );

    @Query("SELECT COUNT(r) > 0 FROM ProblemReviewReminder r WHERE r.userId = :userId AND r.status = :sent AND r.sentAt >= :startOfDay AND r.sentAt < :endOfDay AND r.deletedAt IS NULL")
    boolean hasSentTodayForUser(
            @Param("userId") Long userId,
            @Param("sent") ProblemReviewReminderStatus sent,
            @Param("startOfDay") LocalDateTime startOfDay,
            @Param("endOfDay") LocalDateTime endOfDay
    );

    @Modifying(clearAutomatically = true)
    @Query("UPDATE ProblemReviewReminder r SET r.status = :newStatus WHERE r.id = :id AND r.status = :expectedStatus")
    int tryUpdateStatus(
            @Param("id") Long id,
            @Param("expectedStatus") ProblemReviewReminderStatus expectedStatus,
            @Param("newStatus") ProblemReviewReminderStatus newStatus
    );

    @Modifying(clearAutomatically = true)
    @Query("UPDATE ProblemReviewReminder r SET r.status = :sent, r.sentAt = :sentAt WHERE r.id = :id")
    void markSent(
            @Param("id") Long id,
            @Param("sentAt") LocalDateTime sentAt,
            @Param("sent") ProblemReviewReminderStatus sent
    );

    @Modifying(clearAutomatically = true)
    @Query("UPDATE ProblemReviewReminder r SET r.status = :failed, r.retryCount = r.retryCount + 1, r.lastErrorMessage = :errorMessage WHERE r.id = :id")
    void markFailed(
            @Param("id") Long id,
            @Param("errorMessage") String errorMessage,
            @Param("failed") ProblemReviewReminderStatus failed
    );

    @Modifying(clearAutomatically = true)
    @Query("UPDATE ProblemReviewReminder r SET r.status = :failed, r.retryCount = r.retryCount + 1, r.lastErrorMessage = 'stuck recovery' WHERE r.status = :sending AND r.updatedAt < :stuckBefore AND r.deletedAt IS NULL")
    int recoverStuckRows(
            @Param("sending") ProblemReviewReminderStatus sending,
            @Param("failed") ProblemReviewReminderStatus failed,
            @Param("stuckBefore") LocalDateTime stuckBefore
    );

    @Modifying
    @Query("UPDATE ProblemReviewReminder r SET r.status = :canceled WHERE r.problemId = :problemId AND r.status IN :pendingStatuses AND r.deletedAt IS NULL")
    int cancelByProblem(
            @Param("problemId") Long problemId,
            @Param("canceled") ProblemReviewReminderStatus canceled,
            @Param("pendingStatuses") List<ProblemReviewReminderStatus> pendingStatuses
    );

    @Modifying
    @Query("UPDATE ProblemReviewReminder r SET r.problemMemoSnapshot = :memo, r.problemReferenceSnapshot = :reference WHERE r.problemId = :problemId AND r.status = :scheduled AND r.deletedAt IS NULL")
    int refreshSnapshot(
            @Param("problemId") Long problemId,
            @Param("memo") String memo,
            @Param("reference") String reference,
            @Param("scheduled") ProblemReviewReminderStatus scheduled
    );

    @Modifying
    @Query("UPDATE ProblemReviewReminder r SET r.status = :canceled WHERE r.userId = :userId AND r.status IN :pendingStatuses AND r.deletedAt IS NULL")
    int cancelAllByUser(
            @Param("userId") Long userId,
            @Param("canceled") ProblemReviewReminderStatus canceled,
            @Param("pendingStatuses") List<ProblemReviewReminderStatus> pendingStatuses
    );

    @Modifying
    @Query("UPDATE ProblemReviewReminder r SET r.status = :skipped WHERE r.problemId = :problemId AND r.status = :scheduled AND r.scheduledAt <= :practicedAt AND r.deletedAt IS NULL")
    int skipDuePendingByProblem(
            @Param("problemId") Long problemId,
            @Param("skipped") ProblemReviewReminderStatus skipped,
            @Param("scheduled") ProblemReviewReminderStatus scheduled,
            @Param("practicedAt") LocalDateTime practicedAt
    );
}
