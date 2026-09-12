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
    // 응답에 없으면 프론트(UserInfoModel.dart)가 기본값 true 로 복원해,
    // 알림을 꺼도 앱을 다시 켜면 스위치가 켜진 것처럼 보였다
    boolean notificationEnabled,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    /**
     * 화면에 보여줄 개별 능력치 레벨의 상한.
     *
     * <p>개별 능력치는 도메인 상 상한이 없다. 게이지가 15 칸이라 표시만 눌러 보낸다.
     * 치장 해금표의 능력치별 아이템도 레벨 15 가 마지막이라 이 값은 그대로 둔다.
     */
    private static final Long MAX_ABILITY_LEVEL = 15L;

    /**
     * 화면에 보여줄 총 학습 레벨의 상한.
     *
     * <p>{@link com.aisip.OnO.backend.mission.entity.UserMissionStatus#MAX_TOTAL_STUDY_LEVEL} 과 같은 값이어야 한다.
     * 여기가 15 에 머물면 레벨 16 이상인 사용자에게 앱은 계속 Lv.15 를 보여주고 게이지도 멈춘 것처럼 보이는데,
     * 정작 치장은 총 학습 레벨 16·18·19·20 에서 계속 열린다. 필드 이름과 의미는 그대로고 천장만 함께 올린다.
     */
    private static final Long MAX_TOTAL_STUDY_LEVEL = 20L;

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
                .totalStudyLevel(getTotalStudyResponseLevel(missionStatus.getTotalStudyLevel()))
                .totalStudyCurrentPoint(getTotalStudyResponsePoint(missionStatus.getTotalStudyLevel(), missionStatus.getTotalStudyPoint()))
                .totalStudyNextLevelThreshold(getTotalStudyNextLevelThreshold(missionStatus))
                .notificationEnabled(user.isNotificationEnabled())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    private static Long getResponseLevel(Long level) {
        if (level > MAX_ABILITY_LEVEL) {
            return MAX_ABILITY_LEVEL;
        }
        return level;
    }

    private static Long getResponsePoint(Long level, Long point) {
        if (level > MAX_ABILITY_LEVEL) {
            return getThresholdForLevel(MAX_ABILITY_LEVEL);
        }
        return point;
    }

    private static Long getTotalStudyResponseLevel(Long level) {
        if (level > MAX_TOTAL_STUDY_LEVEL) {
            return MAX_TOTAL_STUDY_LEVEL;
        }
        return level;
    }

    private static Long getTotalStudyResponsePoint(Long level, Long point) {
        if (level > MAX_TOTAL_STUDY_LEVEL) {
            return getTotalStudyThresholdForLevel(MAX_TOTAL_STUDY_LEVEL);
        }
        return point;
    }

    private static Long getTotalStudyNextLevelThreshold(com.aisip.OnO.backend.mission.entity.UserMissionStatus status) {
        if (status.getTotalStudyLevel() >= MAX_TOTAL_STUDY_LEVEL) {
            return getTotalStudyThresholdForLevel(MAX_TOTAL_STUDY_LEVEL);
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
