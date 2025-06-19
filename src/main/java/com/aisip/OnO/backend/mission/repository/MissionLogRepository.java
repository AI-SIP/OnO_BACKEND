package com.aisip.OnO.backend.mission.repository;

import com.aisip.OnO.backend.mission.entity.MissionLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MissionLogRepository extends JpaRepository<MissionLog, Long>, MissionLogRepositoryCustom {
}
