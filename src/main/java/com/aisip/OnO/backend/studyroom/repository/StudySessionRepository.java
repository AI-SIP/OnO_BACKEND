package com.aisip.OnO.backend.studyroom.repository;

import com.aisip.OnO.backend.studyroom.entity.StudySession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface StudySessionRepository extends JpaRepository<StudySession, Long> {

    @Query("select s from StudySession s join fetch s.user where s.room.id = :roomId and s.endedAt is null order by s.startedAt asc")
    List<StudySession> findActiveByRoomId(@Param("roomId") Long roomId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from StudySession s where s.user.id = :userId and s.endedAt is null")
    List<StudySession> findActiveByUserIdForUpdate(@Param("userId") Long userId);

    Optional<StudySession> findByIdAndRoomIdAndUserId(Long id, Long roomId, Long userId);

    List<StudySession> findAllByEndedAtIsNullAndStartedAtBefore(LocalDateTime threshold);
}
