package com.aisip.OnO.backend.studyroom.repository;

import com.aisip.OnO.backend.studyroom.entity.StudyRoomSharedProblemComment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StudyRoomSharedProblemCommentRepository extends JpaRepository<StudyRoomSharedProblemComment, Long> {

    @Query("""
            select c
            from StudyRoomSharedProblemComment c
            join fetch c.author
            where c.sharedProblem.id = :sharedProblemId
              and (:cursor is null or c.id < :cursor)
            order by c.id desc
            """)
    List<StudyRoomSharedProblemComment> findBySharedProblemIdAndCursor(@Param("sharedProblemId") Long sharedProblemId,
                                                                       @Param("cursor") Long cursor,
                                                                       Pageable pageable);

    @Query("""
            select c
            from StudyRoomSharedProblemComment c
            join fetch c.author
            join fetch c.sharedProblem sp
            join fetch sp.room
            where c.id = :commentId
              and sp.id = :sharedProblemId
              and sp.room.id = :roomId
            """)
    Optional<StudyRoomSharedProblemComment> findByIdAndSharedProblemIdAndRoomId(@Param("commentId") Long commentId,
                                                                                @Param("sharedProblemId") Long sharedProblemId,
                                                                                @Param("roomId") Long roomId);

    void deleteBySharedProblemId(Long sharedProblemId);
}
