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
    Long attendanceLevel,
    Long attendancePoint,
    Long noteWriteLevel,
    Long noteWritePoint,
    Long problemPracticeLevel,
    Long problemPracticePoint,
    Long notePracticeLevel,
    Long notePracticePoint,
    Long totalStudyLevel,
    Long totalStudyCurrentPoint,
    Long totalStudyNextLevelThreshold,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static UserResponseDto from(@NotNull User user) {
        return UserResponseDto.builder()
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .attendanceLevel(user.getUserMissionStatus().getAttendanceLevel())
                .attendancePoint(user.getUserMissionStatus().getAttendancePoint())
                .noteWriteLevel(user.getUserMissionStatus().getNoteWriteLevel())
                .noteWritePoint(user.getUserMissionStatus().getNoteWritePoint())
                .problemPracticeLevel(user.getUserMissionStatus().getProblemPracticeLevel())
                .problemPracticePoint(user.getUserMissionStatus().getProblemPracticePoint())
                .notePracticeLevel(user.getUserMissionStatus().getNotePracticeLevel())
                .notePracticePoint(user.getUserMissionStatus().getNotePracticePoint())
                .totalStudyLevel(user.getUserMissionStatus().getTotalStudyLevel())
                .totalStudyCurrentPoint(user.getUserMissionStatus().getTotalStudyCurrentPoint())
                .totalStudyNextLevelThreshold(user.getUserMissionStatus().getTotalStudyNextLevelThreshold())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}