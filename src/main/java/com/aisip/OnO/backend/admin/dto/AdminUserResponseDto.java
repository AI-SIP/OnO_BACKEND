package com.aisip.OnO.backend.admin.dto;

import com.aisip.OnO.backend.user.entity.User;

import java.time.LocalDateTime;

public record AdminUserResponseDto(
        Long userId,
        String name,
        String email,
        Long totalStudyLevel,
        Long totalStudyCurrentPoint,
        Long problemCount,
        LocalDateTime createdAt
) {
    public static AdminUserResponseDto from(User user, Long problemCount) {
        return new AdminUserResponseDto(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getUserMissionStatus().getTotalStudyLevel(),
                user.getUserMissionStatus().getTotalStudyPoint(),
                problemCount != null ? problemCount : 0L,
                user.getCreatedAt()
        );
    }
}
