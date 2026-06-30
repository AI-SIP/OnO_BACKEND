package com.aisip.OnO.backend.studyroom.repository;

import com.aisip.OnO.backend.studyroom.entity.StudyRoomWeeklyReportRead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StudyRoomWeeklyReportReadRepository extends JpaRepository<StudyRoomWeeklyReportRead, Long> {

    boolean existsByReportIdAndUserId(Long reportId, Long userId);

    Optional<StudyRoomWeeklyReportRead> findByReportIdAndUserId(Long reportId, Long userId);

    @Query("select r from StudyRoomWeeklyReportRead r where r.report.id in :reportIds and r.user.id = :userId")
    List<StudyRoomWeeklyReportRead> findAllByReportIdsAndUserId(@Param("reportIds") Collection<Long> reportIds, @Param("userId") Long userId);

    @Query("select count(rp) > 0 from StudyRoomWeeklyReport rp where rp.room.id = :roomId and not exists " +
            "(select rr.id from StudyRoomWeeklyReportRead rr where rr.report = rp and rr.user.id = :userId)")
    boolean existsUnreadReport(@Param("roomId") Long roomId, @Param("userId") Long userId);
}
