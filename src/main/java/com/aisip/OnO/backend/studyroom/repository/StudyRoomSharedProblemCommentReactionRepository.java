package com.aisip.OnO.backend.studyroom.repository;

import com.aisip.OnO.backend.studyroom.entity.StudyRoomSharedProblemCommentReaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StudyRoomSharedProblemCommentReactionRepository extends JpaRepository<StudyRoomSharedProblemCommentReaction, Long> {

    Optional<StudyRoomSharedProblemCommentReaction> findByCommentIdAndUserIdAndEmoji(Long commentId, Long userId, String emoji);

    @Query("select r from StudyRoomSharedProblemCommentReaction r where r.comment.id = :commentId")
    List<StudyRoomSharedProblemCommentReaction> findAllByCommentId(@Param("commentId") Long commentId);

    @Query("select r from StudyRoomSharedProblemCommentReaction r where r.comment.id in :commentIds")
    List<StudyRoomSharedProblemCommentReaction> findAllByCommentIds(@Param("commentIds") Collection<Long> commentIds);

    /** 훈장 '응원단장' 판정용. 누른 자리가 어디든 응원한 것은 응원한 것이라 세 테이블을 합쳐 센다. */
    long countByUserId(Long userId);

    @Modifying
    @Query("delete from StudyRoomSharedProblemCommentReaction r where r.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("delete from StudyRoomSharedProblemCommentReaction r where r.comment.id = :commentId")
    void deleteByCommentId(@Param("commentId") Long commentId);

    @Modifying
    @Query("delete from StudyRoomSharedProblemCommentReaction r where r.comment.sharedProblem.id = :sharedProblemId")
    void deleteBySharedProblemId(@Param("sharedProblemId") Long sharedProblemId);

    @Modifying
    @Query("delete from StudyRoomSharedProblemCommentReaction r where r.comment.author.id = :authorId")
    void deleteByCommentAuthorId(@Param("authorId") Long authorId);
}
