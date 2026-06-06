package com.aisip.OnO.backend.studyroom.repository;

import com.aisip.OnO.backend.studyroom.entity.StudyRoomFeed;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomFeedEventType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface StudyRoomFeedRepository extends JpaRepository<StudyRoomFeed, Long> {

    @Query("select f from StudyRoomFeed f join fetch f.user where f.room.id = :roomId order by f.createdAt desc")
    Page<StudyRoomFeed> findPageWithUserByRoomId(@Param("roomId") Long roomId, Pageable pageable);

    @Query("select f from StudyRoomFeed f join fetch f.user " +
            "where f.room.id = :roomId and (:cursor is null or f.id < :cursor) " +
            "order by f.id desc")
    List<StudyRoomFeed> findWithUserByRoomIdAndCursor(@Param("roomId") Long roomId,
                                                      @Param("cursor") Long cursor,
                                                      Pageable pageable);

    Optional<StudyRoomFeed> findByIdAndRoomId(Long id, Long roomId);

    Optional<StudyRoomFeed> findTopByRoomIdAndUserIdAndEventTypeAndCreatedAtBetweenOrderByCreatedAtDesc(
            Long roomId, Long userId, StudyRoomFeedEventType eventType, LocalDateTime start, LocalDateTime end);
}
