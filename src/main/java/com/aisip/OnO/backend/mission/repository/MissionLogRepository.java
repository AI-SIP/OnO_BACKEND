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

    @Query("SELECT m FROM MissionLog m JOIN FETCH m.user WHERE m.missionType = :missionType")
    List<MissionLog> findAllByMissionTypeWithUser(@Param("missionType") MissionType missionType, Pageable pageable);

    long countByMissionType(MissionType missionType);

    /**
     * 한 사용자가 그 미션을 남긴 날짜들. 하루에 여러 번이어도 하루로 접힌다.
     *
     * <p>훈장 '개근'이 연속 30 일을 세는 데 쓴다. 행을 전부 읽어 자바에서 날짜로 접으면 로그인이
     * 수천 건인 사용자의 행을 통째로 올리게 되므로 날짜로 접는 것까지 DB 에 맡긴다.
     *
     * <p>{@code created_at} 은 JPA Auditing 이 JVM 기본 시간대로 채우고, 운영과 테스트 모두
     * {@code -Duser.timezone=Asia/Seoul} 이 걸려 있다. 즉 저장된 값이 이미 KST 벽시계라
     * {@code DATE()} 가 그대로 KST 날짜다. 여기서 시간대를 한 번 더 옮기면 아홉 시간이 밀린다.
     *
     * <p>반환 타입을 {@code Object} 로 둔다. {@code FUNCTION('DATE', ...)} 의 자바 타입은
     * Hibernate 가 정하지 않고 JDBC 드라이버가 주는 대로라, 드라이버 버전에 따라
     * {@code java.sql.Date} 일 수도 {@code LocalDate} 일 수도 있다.
     * 호출부가 둘 다 받아 넘긴다({@code MissionLogService.toLocalDate} 도 같은 처리를 한다).
     */
    @Query("""
            SELECT DISTINCT FUNCTION('DATE', m.createdAt)
            FROM MissionLog m
            WHERE m.user.id = :userId
              AND m.missionType = :missionType
            """)
    List<Object> findDistinctLogDates(
            @Param("userId") Long userId,
            @Param("missionType") MissionType missionType
    );

    long countByMissionTypeAndCreatedAtBetween(
            MissionType missionType,
            LocalDateTime startDateTime,
            LocalDateTime endDateTime
    );

    @Query("""
            SELECT COUNT(DISTINCT m.user.id)
            FROM MissionLog m
            WHERE m.missionType = :missionType
              AND m.createdAt BETWEEN :startDateTime AND :endDateTime
            """)
    long countDistinctUsersByMissionTypeAndCreatedAtBetween(
            @Param("missionType") MissionType missionType,
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime
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
