package com.aisip.OnO.backend.practicenote.entity;

import com.aisip.OnO.backend.practicenote.dto.PracticeNotificationRegisterDto;
import jakarta.persistence.Embeddable;
import lombok.Getter;

@Getter
@Embeddable
public class PracticeNotification {
    private Integer intervalDays;
    private Integer hour;
    private Integer minute;
    private Integer notifyCount;

    protected PracticeNotification() {}

    public PracticeNotification(Integer intervalDays, Integer hour, Integer minute, Integer notifyCount) {
        this.intervalDays = intervalDays;
        this.hour         = hour;
        this.minute       = minute;
        this.notifyCount  = notifyCount;
    }

    public static PracticeNotification from(PracticeNotificationRegisterDto practiceNotificationRegisterDto) {
        if(practiceNotificationRegisterDto == null) {
            return null;
        }

        return new PracticeNotification(
                practiceNotificationRegisterDto.intervalDays(),
                practiceNotificationRegisterDto.hour(),
                practiceNotificationRegisterDto.minute(),
                practiceNotificationRegisterDto.notifyCount()
        );
    }
}
