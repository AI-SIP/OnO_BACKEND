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
     * <p>15 로 두는 동안 도메인 쪽에는 상한이 아예 없어서, 내부 레벨은 무한정 오르고 여기서만 잘렸다.
     * 앱에 보이는 레벨과 실제 레벨이 갈라진 것이다. 이제 도메인이 직접 멈추고 여기는 그 값을 따라간다.
     * 숫자를 다시 적지 않고 {@link com.aisip.OnO.backend.mission.entity.UserMissionStatus#MAX_ABILITY_LEVEL}
     * 을 그대로 참조해, 한쪽만 고쳐서 다시 어긋나는 일이 없게 한다.
     *
     * <p>치장 해금표의 능력치별 아이템은 여전히 레벨 15 가 마지막이다. 16~20 은 해금 보상 없이
     * 게이지만 계속 오르는 구간이다.
     */
    private static final Long MAX_ABILITY_LEVEL =
            com.aisip.OnO.backend.mission.entity.UserMissionStatus.MAX_ABILITY_LEVEL;

    /**
     * 화면에 보여줄 총 학습 레벨의 상한.
     *
     * <p>{@link com.aisip.OnO.backend.mission.entity.UserMissionStatus#MAX_TOTAL_STUDY_LEVEL} 과 같은 값이어야 해서
     * 역시 그 상수를 직접 참조한다. 여기가 낮으면 그 위 레벨인 사용자에게 앱은 계속 낮은 Lv 를 보여주고
     * 게이지도 멈춘 것처럼 보이는데, 정작 치장은 총 학습 레벨 16·18·19·20 에서 계속 열린다.
     */
    private static final Long MAX_TOTAL_STUDY_LEVEL =
            com.aisip.OnO.backend.mission.entity.UserMissionStatus.MAX_TOTAL_STUDY_LEVEL;

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
        // 상한에 닿은 뒤에도 포인트는 계속 쌓인다. 그대로 내보내면 게이지 분모를 넘겨 칸이 넘친다.
        // 상한 미만에서는 잔여 포인트가 항상 임계값보다 작아 이 클램프가 걸리지 않는다.
        return Math.min(point, getThresholdForLevel(level));
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
        return Math.min(point, getTotalStudyThresholdForLevel(level));
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
