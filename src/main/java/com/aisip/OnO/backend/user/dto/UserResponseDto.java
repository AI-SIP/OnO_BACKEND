package com.aisip.OnO.backend.user.dto;

import com.aisip.OnO.backend.user.entity.User;

import java.time.LocalDateTime;

public record UserResponseDto (
    Long userId,
    String name,
    String email,
    String identifier,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static UserResponseDto from(User user) {
        return new UserResponseDto(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getIdentifier(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}