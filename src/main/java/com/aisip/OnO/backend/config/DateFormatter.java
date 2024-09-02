package com.aisip.OnO.backend.config;

import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Component
public class DateFormatter {

    public String format(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "날짜 정보 없음";  // null일 때 반환할 기본 메시지 또는 다른 값을 설정할 수 있습니다.
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy년 MM월 dd일 HH시 mm분 ss초", Locale.KOREAN);
        return dateTime.format(formatter);
    }
}