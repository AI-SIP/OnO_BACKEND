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
    private int level;
    private int point;

    public void gainPoint(int value) {
        this.point += value;
        while (this.point >= getThresholdForLevel(level)) {
            this.point -= getThresholdForLevel(level);
            this.level += 1;
        }
    }

    private int getThresholdForLevel(int level) {
        return 100 + (level - 1) * 20;
    }
}
