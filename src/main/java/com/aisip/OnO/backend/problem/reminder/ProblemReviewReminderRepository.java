package com.aisip.OnO.backend.problem.reminder;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface ProblemReviewReminderRepository extends JpaRepository<ProblemReviewReminder, Long> {

    boolean existsByProblemIdAndSequence(Long problemId, int sequence);

    /**
     * 지금 보낼 예약을 고른다.
     *
     * <p>예전에는 "오늘 이미 처리한 사용자" 를 빼서 사용자당 하루 한 건만 나갔다. 지금은 문제마다
     * 예약 시각에 보내고, 하루 상한은 {@link #countTodayPushesByUser} 로 푸시 수를 세어 서비스에서 건다.
     */
    @Query("""
            SELECT r FROM ProblemReviewReminder r
            WHERE r.status = :status
              AND r.scheduledAt <= :now
              AND r.deletedAt IS NULL
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
            Pageable pageable
    );

    /**
     * 사용자별로 오늘 나간 푸시 수를 센다. 행 수가 아니다.
     *
     * <p>한 번에 묶어 보낸 행들은 같은 폴링 시각을 공유한다. 선점 때 {@code updatedAt} 에,
     * 발송 기록 때 {@code sentAt} 에 같은 {@code now} 가 들어가므로 그 시각의 가짓수가 곧 푸시 수다.
     * 선점만 되고 아직 SENT 가 아닌 SENDING 행도 이미 나가는 중인 푸시라 함께 센다.
     */
    @Query("""
            SELECT r.userId AS userId,
                   COUNT(DISTINCT CASE WHEN r.status = :sent THEN r.sentAt ELSE r.updatedAt END) AS pushCount
            FROM ProblemReviewReminder r
            WHERE r.userId IN :userIds
              AND r.deletedAt IS NULL
              AND (
                  (r.status = :sent AND r.sentAt >= :startOfDay AND r.sentAt < :endOfDay)
                  OR
                  (r.status = :sending AND r.updatedAt >= :startOfDay AND r.updatedAt < :endOfDay)
              )
            GROUP BY r.userId
            """)
    List<ReminderPushCount> countTodayPushesByUser(
            @Param("userIds") Collection<Long> userIds,
            @Param("sent") ProblemReviewReminderStatus sent,
            @Param("sending") ProblemReviewReminderStatus sending,
            @Param("startOfDay") LocalDateTime startOfDay,
            @Param("endOfDay") LocalDateTime endOfDay
    );

    @Modifying(clearAutomatically = true)
    @Query("UPDATE ProblemReviewReminder r SET r.status = :newStatus, r.updatedAt = :updatedAt WHERE r.id = :id AND r.status = :expectedStatus")
    int tryUpdateStatus(
            @Param("id") Long id,
            @Param("expectedStatus") ProblemReviewReminderStatus expectedStatus,
            @Param("newStatus") ProblemReviewReminderStatus newStatus,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    /**
     * 선점(SENDING)된 행만 SENT 로 바꾼다. 선점과 기록 사이에 문제 삭제나 탈퇴로 CANCELED 가 된 행,
     * stuck 복구로 FAILED 가 된 행은 덮지 않는다.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE ProblemReviewReminder r SET r.status = :sent, r.sentAt = :sentAt WHERE r.id = :id AND r.status = :sending")
    int markSent(
            @Param("id") Long id,
            @Param("sentAt") LocalDateTime sentAt,
            @Param("sending") ProblemReviewReminderStatus sending,
            @Param("sent") ProblemReviewReminderStatus sent
    );

    /** 선점(SENDING)된 행만 FAILED 로 바꾼다. 그사이 CANCELED 가 된 행은 덮지 않는다. */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE ProblemReviewReminder r SET r.status = :failed, r.retryCount = r.retryCount + 1, r.lastErrorMessage = :errorMessage WHERE r.id = :id AND r.status = :sending")
    int markFailed(
            @Param("id") Long id,
            @Param("errorMessage") String errorMessage,
            @Param("sending") ProblemReviewReminderStatus sending,
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

    /**
     * 취소할 예약의 ID 만 먼저 읽는다. 잠금을 잡지 않는 일반 조회다. (#319)
     *
     * @see #cancelByIdIn
     */
    @Query("SELECT r.id FROM ProblemReviewReminder r WHERE r.problemId = :problemId AND r.status IN :pendingStatuses AND r.deletedAt IS NULL")
    List<Long> findPendingIdsByProblem(
            @Param("problemId") Long problemId,
            @Param("pendingStatuses") List<ProblemReviewReminderStatus> pendingStatuses
    );

    /**
     * 예약을 <b>기본 키로</b> 취소한다. (#319)
     *
     * <p>예전에는 {@code WHERE problem_id = ?} 로 한 번에 UPDATE 했다. 그러면 REPEATABLE READ 에서
     * {@code uq_problem_review_reminder_seq(problem_id, sequence)} 를 범위로 훑으면서 next-key lock 이
     * 마지막 일치 항목 뒤의 <b>갭까지</b> 잡는다. {@code problem_id} 는 계속 커지므로 그 갭은 대개
     * supremum(인덱스 끝) 이고, 그러면 <b>그 뒤에 등록되는 모든 문제</b>의 예약 INSERT 가
     * 사용자와 무관하게 전부 막힌다. 문제 등록은 커밋 직후 예약을 넣기 때문에
     * ({@code scheduleForNewProblems}), 폴더 하나 지우는 동안 다른 계정의 등록까지 잠금 대기에 걸렸다.
     *
     * <p>기본 키 등치 조회는 {@code REC_NOT_GAP} 이라 갭을 잡지 않는다.
     */
    @Modifying
    @Query("UPDATE ProblemReviewReminder r SET r.status = :canceled WHERE r.id IN :ids")
    int cancelByIdIn(
            @Param("ids") Collection<Long> ids,
            @Param("canceled") ProblemReviewReminderStatus canceled
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
