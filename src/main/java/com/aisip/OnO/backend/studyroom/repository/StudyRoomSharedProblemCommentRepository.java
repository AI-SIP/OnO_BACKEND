package com.aisip.OnO.backend.studyroom.repository;

import com.aisip.OnO.backend.studyroom.entity.StudyRoomSharedProblemComment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Collection;

public interface StudyRoomSharedProblemCommentRepository extends JpaRepository<StudyRoomSharedProblemComment, Long> {

    @Query("""
            select c
            from StudyRoomSharedProblemComment c
            join fetch c.sharedProblem sp
            join fetch sp.room
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

    @Query("""
            select c.sharedProblem.id, count(c.id)
            from StudyRoomSharedProblemComment c
            where c.sharedProblem.id in :sharedProblemIds
            group by c.sharedProblem.id
            """)
    List<Object[]> countBySharedProblemIds(@Param("sharedProblemIds") Collection<Long> sharedProblemIds);

    @Modifying
    @Query("delete from StudyRoomSharedProblemComment c where c.sharedProblem.id = :sharedProblemId")
    void deleteBySharedProblemId(@Param("sharedProblemId") Long sharedProblemId);

    @Modifying
    @Query("delete from StudyRoomSharedProblemComment c where c.author.id = :authorId")
    void deleteByAuthorId(@Param("authorId") Long authorId);
}
