package com.aisip.OnO.backend.mission.repository;

import com.aisip.OnO.backend.mission.entity.MissionType;
import com.aisip.OnO.backend.mission.entity.MissionLog;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MissionLogRepository extends JpaRepository<MissionLog, Long>, MissionLogRepositoryCustom {
    List<MissionLog> findAllByUserId(Long userId);

    List<MissionLog> findAllByMissionType(MissionType missionType, Pageable pageable);

    long countByMissionType(MissionType missionType);

    long countByMissionTypeAndCreatedAtBetween(
            MissionType missionType,
            LocalDateTime startDateTime,
            LocalDateTime endDateTime
    );

    @Query("""
            SELECT FUNCTION('DATE', m.createdAt), COUNT(m)
            FROM MissionLog m
            WHERE m.missionType = :missionType
              AND m.createdAt BETWEEN :startDateTime AND :endDateTime
            GROUP BY FUNCTION('DATE', m.createdAt)
            """)
    List<Object[]> countDailyByMissionType(
            @Param("missionType") MissionType missionType,
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime
    );
}
