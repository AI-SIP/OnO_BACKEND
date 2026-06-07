package com.aisip.OnO.backend.studyroom.repository;

import com.aisip.OnO.backend.studyroom.entity.StudyRoomSharedProblemReaction;
import org.springframework.data.jpa.repository.JpaRepository;
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

    void deleteBySharedProblemId(Long sharedProblemId);
}
