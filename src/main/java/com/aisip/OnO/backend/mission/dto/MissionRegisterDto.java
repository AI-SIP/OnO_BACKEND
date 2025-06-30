package com.aisip.OnO.backend.mission.dto;

import com.aisip.OnO.backend.mission.entity.MissionType;

public record MissionRegisterDto (
    Long userId,
    MissionType missionType,
    Long referenceId
) {
}
