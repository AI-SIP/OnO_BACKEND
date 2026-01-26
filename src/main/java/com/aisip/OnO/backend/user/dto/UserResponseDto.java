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
                // DB에 저장된 총 학습 레벨 정보 사용 (계산 불필요)
                .totalStudyLevel(user.getUserMissionStatus().getTotalStudyLevel())
                .totalStudyCurrentPoint(user.getUserMissionStatus().getTotalStudyPoint())
                .totalStudyNextLevelThreshold(getTotalStudyNextLevelThreshold(user.getUserMissionStatus()))
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    private static Long getTotalStudyNextLevelThreshold(com.aisip.OnO.backend.mission.entity.UserMissionStatus status) {
        if (status.getTotalStudyLevel() >= 15) {
            return 0L;
        }
        // 개별 능력치 필요 경험치 × 4
        return (10 + (status.getTotalStudyLevel() - 1) * 10) * 4;
    }
}