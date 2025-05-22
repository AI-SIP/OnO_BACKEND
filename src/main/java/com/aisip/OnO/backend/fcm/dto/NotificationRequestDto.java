package com.aisip.OnO.backend.fcm.dto;

import java.util.Map;

public record NotificationRequestDto (
        String token,
        String title,
        String body,
        Map<String, String> data
){
}
