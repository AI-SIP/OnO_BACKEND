package com.aisip.OnO.backend.studyroom.repository;

import com.aisip.OnO.backend.studyroom.entity.StudyRoomInviteCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface StudyRoomInviteCodeRepository extends JpaRepository<StudyRoomInviteCode, Long> {

    Optional<StudyRoomInviteCode> findTopByRoomIdOrderByExpiredAtDesc(Long roomId);

    Optional<StudyRoomInviteCode> findByCode(String code);

    boolean existsByCode(String code);

    @Modifying
    @Query("delete from StudyRoomInviteCode c where c.expiredAt < :threshold")
    void deleteExpiredBefore(@Param("threshold") LocalDateTime threshold);
}
