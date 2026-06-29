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
    String profileImageUrl,
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
    private static final Long MAX_LEVEL = 15L;

    public static UserResponseDto from(@NotNull User user) {
        var missionStatus = user.getUserMissionStatus();

        return UserResponseDto.builder()
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .profileImageUrl(user.getProfileImageUrl())
                .attendanceLevel(getResponseLevel(missionStatus.getAttendanceLevel()))
                .attendancePoint(getResponsePoint(missionStatus.getAttendanceLevel(), missionStatus.getAttendancePoint()))
                .noteWriteLevel(getResponseLevel(missionStatus.getNoteWriteLevel()))
                .noteWritePoint(getResponsePoint(missionStatus.getNoteWriteLevel(), missionStatus.getNoteWritePoint()))
                .problemPracticeLevel(getResponseLevel(missionStatus.getProblemPracticeLevel()))
                .problemPracticePoint(getResponsePoint(missionStatus.getProblemPracticeLevel(), missionStatus.getProblemPracticePoint()))
                .notePracticeLevel(getResponseLevel(missionStatus.getNotePracticeLevel()))
                .notePracticePoint(getResponsePoint(missionStatus.getNotePracticeLevel(), missionStatus.getNotePracticePoint()))
                // DB에 저장된 총 학습 레벨 정보 사용 (계산 불필요)
                .totalStudyLevel(getResponseLevel(missionStatus.getTotalStudyLevel()))
                .totalStudyCurrentPoint(getTotalStudyResponsePoint(missionStatus.getTotalStudyLevel(), missionStatus.getTotalStudyPoint()))
                .totalStudyNextLevelThreshold(getTotalStudyNextLevelThreshold(missionStatus))
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    private static Long getResponseLevel(Long level) {
        if (level > MAX_LEVEL) {
            return MAX_LEVEL;
        }
        return level;
    }

    private static Long getResponsePoint(Long level, Long point) {
        if (level > MAX_LEVEL) {
            return getThresholdForLevel(MAX_LEVEL);
        }
        return point;
    }

    private static Long getTotalStudyResponsePoint(Long level, Long point) {
        if (level > MAX_LEVEL) {
            return getTotalStudyThresholdForLevel(MAX_LEVEL);
        }
        return point;
    }

    private static Long getTotalStudyNextLevelThreshold(com.aisip.OnO.backend.mission.entity.UserMissionStatus status) {
        if (status.getTotalStudyLevel() >= MAX_LEVEL) {
            return getTotalStudyThresholdForLevel(MAX_LEVEL);
        }
        // 개별 능력치 필요 경험치 × 4
        return getTotalStudyThresholdForLevel(status.getTotalStudyLevel());
    }

    private static Long getThresholdForLevel(Long level) {
        return 10 + (level - 1) * 10;
    }

    private static Long getTotalStudyThresholdForLevel(Long level) {
        return getThresholdForLevel(level) * 4;
    }
}
