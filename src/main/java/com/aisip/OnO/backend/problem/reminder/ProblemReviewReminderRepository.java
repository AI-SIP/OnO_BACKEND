package com.aisip.OnO.backend.problem.reminder;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface ProblemReviewReminderRepository extends JpaRepository<ProblemReviewReminder, Long> {

    boolean existsByProblemIdAndSequence(Long problemId, int sequence);

    @Query("""
            SELECT r FROM ProblemReviewReminder r
            WHERE r.status = :status
              AND r.scheduledAt <= :now
              AND r.deletedAt IS NULL
              AND NOT EXISTS (
                  SELECT 1 FROM ProblemReviewReminder sent
                  WHERE sent.userId = r.userId
                    AND sent.status = :sent
                    AND sent.sentAt >= :startOfDay
                    AND sent.sentAt < :endOfDay
                    AND sent.deletedAt IS NULL
              )
              AND EXISTS (
                  SELECT 1 FROM User u
                  WHERE u.id = r.userId
                    AND u.notificationEnabled = true
              )
              AND EXISTS (
                  SELECT 1 FROM FcmToken t
                  WHERE t.userId = r.userId
              )
            ORDER BY r.scheduledAt ASC, r.userId ASC
            """)
    List<ProblemReviewReminder> findDueReminders(
            @Param("status") ProblemReviewReminderStatus status,
            @Param("now") LocalDateTime now,
            @Param("sent") ProblemReviewReminderStatus sent,
            @Param("startOfDay") LocalDateTime startOfDay,
            @Param("endOfDay") LocalDateTime endOfDay,
            Pageable pageable
    );

    @Modifying(clearAutomatically = true)
    @Query("UPDATE ProblemReviewReminder r SET r.status = :newStatus, r.updatedAt = :updatedAt WHERE r.id = :id AND r.status = :expectedStatus")
    int tryUpdateStatus(
            @Param("id") Long id,
            @Param("expectedStatus") ProblemReviewReminderStatus expectedStatus,
            @Param("newStatus") ProblemReviewReminderStatus newStatus,
            @Param("updatedAt") LocalDateTime updatedAt
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

    @Transactional
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
