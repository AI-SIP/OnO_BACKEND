package com.aisip.OnO.backend.user.dto;

import com.aisip.OnO.backend.user.entity.User;
import lombok.AccessLevel;
import lombok.Builder;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDateTime;

@Builder(access = AccessLevel.PRIVATE)
public record UserResponseDto (
    Long userId,
    String name,
    String email,
    Long level,
    Long point,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static UserResponseDto from(@NotNull User user) {
        return UserResponseDto.builder()
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .level(user.getUserMissionStatus().getLevel())
                .point(user.getUserMissionStatus().getPoint())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}