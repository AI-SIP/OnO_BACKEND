package com.aisip.OnO.backend.studyroom.repository;

import com.aisip.OnO.backend.studyroom.entity.StudyRoomSharedProblemReaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StudyRoomSharedProblemReactionRepository extends JpaRepository<StudyRoomSharedProblemReaction, Long> {

    Optional<StudyRoomSharedProblemReaction> findBySharedProblemIdAndUserIdAndEmoji(Long sharedProblemId, Long userId, String emoji);

    @Query("select r from StudyRoomSharedProblemReaction r where r.sharedProblem.id in :sharedProblemIds")
    List<StudyRoomSharedProblemReaction> findAllBySharedProblemIds(@Param("sharedProblemIds") Collection<Long> sharedProblemIds);

    List<StudyRoomSharedProblemReaction> findAllBySharedProblemId(Long sharedProblemId);

    /** 훈장 '응원단장' 판정용. 누른 자리가 어디든 응원한 것은 응원한 것이라 세 테이블을 합쳐 센다. */
    long countByUserId(Long userId);

    @Modifying
    @Query("delete from StudyRoomSharedProblemReaction r where r.sharedProblem.id = :sharedProblemId")
    void deleteBySharedProblemId(@Param("sharedProblemId") Long sharedProblemId);
}
