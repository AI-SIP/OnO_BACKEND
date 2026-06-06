package com.aisip.OnO.backend.studyroom.repository;

import com.aisip.OnO.backend.studyroom.entity.StudyRoomSharedProblem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StudyRoomSharedProblemRepository extends JpaRepository<StudyRoomSharedProblem, Long> {

    @Query("select sp from StudyRoomSharedProblem sp join fetch sp.sharedByUser join fetch sp.problem where sp.room.id = :roomId order by sp.createdAt desc")
    Page<StudyRoomSharedProblem> findPageByRoomId(@Param("roomId") Long roomId, Pageable pageable);

    @Query("select sp from StudyRoomSharedProblem sp join fetch sp.sharedByUser join fetch sp.problem " +
            "where sp.room.id = :roomId and (:cursor is null or sp.id < :cursor) " +
            "order by sp.id desc")
    List<StudyRoomSharedProblem> findByRoomIdAndCursor(@Param("roomId") Long roomId,
                                                       @Param("cursor") Long cursor,
                                                       Pageable pageable);

    Optional<StudyRoomSharedProblem> findByIdAndRoomId(Long id, Long roomId);
}
