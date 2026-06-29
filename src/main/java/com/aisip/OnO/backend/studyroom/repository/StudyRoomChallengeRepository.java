package com.aisip.OnO.backend.studyroom.repository;

import com.aisip.OnO.backend.studyroom.entity.StudyRoomChallenge;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomChallengeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface StudyRoomChallengeRepository extends JpaRepository<StudyRoomChallenge, Long> {

    List<StudyRoomChallenge> findAllByRoomIdOrderByEndAtAsc(Long roomId);

    @Query("select c from StudyRoomChallenge c " +
            "where c.room.id = :roomId and c.status = :status and c.endAt >= :now " +
            "order by c.endAt asc")
    List<StudyRoomChallenge> findActiveByRoomId(@Param("roomId") Long roomId,
                                                @Param("status") StudyRoomChallengeStatus status,
                                                @Param("now") LocalDateTime now);

    Optional<StudyRoomChallenge> findByIdAndRoomId(Long id, Long roomId);

    long countByRoomIdAndStatus(Long roomId, StudyRoomChallengeStatus status);

    @Query("select c from StudyRoomChallenge c where c.room.id in :roomIds and c.status = :status")
    List<StudyRoomChallenge> findAllByRoomIdsAndStatus(@Param("roomIds") Collection<Long> roomIds,
                                                       @Param("status") StudyRoomChallengeStatus status);

    @Query("select c.room.id, count(c.id) from StudyRoomChallenge c " +
            "where c.room.id in :roomIds and c.status = :status and c.endAt between :start and :end " +
            "group by c.room.id")
    List<Object[]> countByRoomIdsAndStatusAndEndAtBetween(@Param("roomIds") Collection<Long> roomIds,
                                                          @Param("status") StudyRoomChallengeStatus status,
                                                          @Param("start") LocalDateTime start,
                                                          @Param("end") LocalDateTime end);

    /**
     * IN_PROGRESS 상태인 챌린지를 원자적으로 targetStatus로 전이.
     * 동시 호출 시 단 하나의 스레드만 1을 반환 — FCM 중복 발송 방지에 사용.
     */
    @Modifying
    @Query("update StudyRoomChallenge c set c.status = :targetStatus, c.completedAt = :now " +
            "where c.id = :id and c.status = 'IN_PROGRESS'")
    int tryTransitionFromInProgress(@Param("id") Long id,
                                    @Param("targetStatus") StudyRoomChallengeStatus targetStatus,
                                    @Param("now") LocalDateTime now);
}
