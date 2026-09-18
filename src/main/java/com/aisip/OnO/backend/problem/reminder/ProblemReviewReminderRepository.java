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

    /**
     * "오늘 이 사용자를 아직 건드리지 않았는가" 로 후보를 고른다.
     *
     * <p>SENT 만 보면 선점(SENDING)과 SENT 기록 사이에 창이 생긴다. 선점은 별도 트랜잭션으로 바로
     * 커밋되므로, 인스턴스 A 가 예약 X 를 SENDING 으로 커밋한 직후 인스턴스 B 가 폴링하면 X 는
     * SCHEDULED 가 아니라 후보에서 빠지고 그 사용자의 SENT 행도 아직 없어서, 같은 사용자의 다른
     * 예약 Y 가 하루 두 번째 푸시로 나갔다. 그래서 오늘 선점된 SENDING 행도 "이미 처리함" 으로 센다.
     *
     * <p>선점 시각은 {@code tryUpdateStatus} 가 넣는 {@code updatedAt} 이다. SENDING 이 영영 막지는
     * 않는다 — 10분 넘게 멈춘 행은 stuck 복구가 FAILED 로 넘긴다.
     */
    @Query("""
            SELECT r FROM ProblemReviewReminder r
            WHERE r.status = :status
              AND r.scheduledAt <= :now
              AND r.deletedAt IS NULL
              AND NOT EXISTS (
                  SELECT 1 FROM ProblemReviewReminder handled
                  WHERE handled.userId = r.userId
                    AND handled.deletedAt IS NULL
                    AND (
                        (handled.status = :sent
                            AND handled.sentAt >= :startOfDay AND handled.sentAt < :endOfDay)
                        OR
                        (handled.status = :sending
                            AND handled.updatedAt >= :startOfDay AND handled.updatedAt < :endOfDay)
                    )
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
            @Param("sending") ProblemReviewReminderStatus sending,
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
    @Query("UPDATE ProblemReviewReminder r SET r.status = :expired, r.updatedAt = :updatedAt WHERE r.status = :scheduled AND r.scheduledAt < :expireBefore AND r.deletedAt IS NULL")
    int expireOverdueRows(
            @Param("scheduled") ProblemReviewReminderStatus scheduled,
            @Param("expired") ProblemReviewReminderStatus expired,
            @Param("expireBefore") LocalDateTime expireBefore,
            @Param("updatedAt") LocalDateTime updatedAt
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
