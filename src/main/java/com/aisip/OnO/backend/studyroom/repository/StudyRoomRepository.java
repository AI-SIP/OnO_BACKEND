package com.aisip.OnO.backend.studyroom.repository;

import com.aisip.OnO.backend.studyroom.entity.StudyRoom;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StudyRoomRepository extends JpaRepository<StudyRoom, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from StudyRoom r where r.id = :roomId")
    Optional<StudyRoom> findByIdForUpdate(@Param("roomId") Long roomId);

    @Query("select r from StudyRoom r where r.id > :cursor order by r.id asc")
    List<StudyRoom> findBatchAfterId(@Param("cursor") Long cursor, Pageable pageable);
}
