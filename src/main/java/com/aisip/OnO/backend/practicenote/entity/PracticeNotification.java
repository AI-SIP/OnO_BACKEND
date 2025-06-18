package com.aisip.OnO.backend.practicenote.entity;

import com.aisip.OnO.backend.practicenote.dto.PracticeNotificationRegisterDto;
import jakarta.persistence.Embeddable;
import lombok.Getter;

import java.util.List;

@Getter
@Embeddable
public class PracticeNotification {
    private Integer intervalDays;
    private Integer hour;
    private Integer minute;
    private String repeatType;
    private List<Integer> weekDays;

    protected PracticeNotification() {}

    public PracticeNotification(Integer intervalDays, Integer hour, Integer minute, String repeatType, List<Integer> weekDays) {
        this.intervalDays = intervalDays;
        this.hour         = hour;
        this.minute       = minute;
        this.repeatType = repeatType;
        this.weekDays      = weekDays;
    }

    public static PracticeNotification from(PracticeNotificationRegisterDto practiceNotificationRegisterDto) {
        if(practiceNotificationRegisterDto == null) {
            return null;
        }

        return new PracticeNotification(
                practiceNotificationRegisterDto.intervalDays(),
                practiceNotificationRegisterDto.hour(),
                practiceNotificationRegisterDto.minute(),
                practiceNotificationRegisterDto.repeatType(),
                practiceNotificationRegisterDto.weekDays()
        );
    }
}
