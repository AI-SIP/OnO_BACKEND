package com.aisip.OnO.backend.fcm.dto;

import com.aisip.OnO.backend.fcm.entity.FcmToken;
import lombok.AccessLevel;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder(access = AccessLevel.PRIVATE)
public record FcmTokenResponseDto(
        Long id,
        Long userId,
        String token,
        LocalDateTime createdAt
) {
    public static FcmTokenResponseDto from(FcmToken fcmToken) {
        return FcmTokenResponseDto.builder()
                .id(fcmToken.getId())
                .userId(fcmToken.getUserId())
                .token(fcmToken.getToken())
                .createdAt(fcmToken.getCreatedAt())
                .build();
    }
}
