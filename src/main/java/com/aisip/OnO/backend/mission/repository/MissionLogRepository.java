package com.aisip.OnO.backend.mission.repository;

import com.aisip.OnO.backend.mission.entity.MissionLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MissionLogRepository extends JpaRepository<MissionLog, Long>, MissionLogRepositoryCustom {
    List<MissionLog> findAllByUserId(Long userId);
}
