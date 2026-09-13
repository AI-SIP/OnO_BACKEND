package com.aisip.OnO.backend.studyroom.repository;

import com.aisip.OnO.backend.studyroom.entity.StudyRoomFeedReaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StudyRoomFeedReactionRepository extends JpaRepository<StudyRoomFeedReaction, Long> {

    Optional<StudyRoomFeedReaction> findByFeedIdAndUserIdAndEmoji(Long feedId, Long userId, String emoji);

    @Query("select r from StudyRoomFeedReaction r where r.feed.id in :feedIds")
    List<StudyRoomFeedReaction> findAllByFeedIds(@Param("feedIds") Collection<Long> feedIds);

    List<StudyRoomFeedReaction> findAllByFeedId(Long feedId);

    /** 훈장 '응원단장' 판정용. 누른 자리가 어디든 응원한 것은 응원한 것이라 세 테이블을 합쳐 센다. */
    long countByUserId(Long userId);
}
