package com.aisip.OnO.backend.mission.entity;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UserMissionStatus {
    private Long level;
    private Long point;

    public void gainPoint(Long value) {
        this.point += value;
        while (this.point >= getThresholdForLevel(level)) {
            this.point -= getThresholdForLevel(level);
            this.level += 1;
        }
    }

    private Long getThresholdForLevel(Long level) {
        return 100 + (level - 1) * 20;
    }
}
