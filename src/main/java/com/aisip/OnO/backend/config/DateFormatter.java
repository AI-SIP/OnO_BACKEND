package com.aisip.OnO.backend.config;

import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Component
public class DateFormatter {

    public String format(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "날짜 정보 없음";
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy년 MM월 dd일 HH시 mm분 ss초", Locale.KOREAN);
        return dateTime.format(formatter);
    }
}