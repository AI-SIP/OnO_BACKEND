package com.aisip.OnO.backend.admin.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 관리자 유저 목록과 상세 화면이 쓰는 조회 결과.
 *
 * <p>화면 한 장을 채우는 행 종류가 많아서 파일을 하나씩 만들지 않고 여기에 모았다.
 * 전부 {@code AdminUserQueryRepository} 가 SQL 결과를 그대로 옮겨 담는 값이다.
 */
public final class AdminUserRows {

    private AdminUserRows() {
    }

    /** 유저 목록의 한 줄. */
    public record ListRow(
            Long userId,
            String name,
            String email,
            String platform,
            Long totalStudyLevel,
            Long totalStudyPoint,
            long problemCount,
            long solveCount,
            long practiceNoteCount,
            LocalDateTime lastActiveAt,
            LocalDateTime createdAt
    ) {
    }

    /** 유저 목록 위의 요약. 검색 조건과 상관없이 전체 기준이다. */
    public record Summary(
            long totalUsers,
            long todaySignups,
            long guestUsers,
            long activeUsersLast7Days
    ) {
        public double guestRate() {
            return totalUsers == 0 ? 0.0 : (double) guestUsers * 100 / totalUsers;
        }
    }

    public record Profile(
            Long userId,
            String name,
            String email,
            String platform,
            String profileImageUrl,
            boolean notificationEnabled,
            LocalDateTime lastActiveAt,
            LocalDate lastNotifiedAt,
            LocalDateTime createdAt,
            Level attendance,
            Level noteWrite,
            Level problemPractice,
            Level notePractice,
            Level totalStudy
    ) {
        /** 앱 번들 경로(assets/...)나 로컬 주소는 브라우저에서 열리지 않아 이미지로 그리지 않는다. */
        public boolean hasViewableProfileImage() {
            return profileImageUrl != null && profileImageUrl.startsWith("https://");
        }
    }

    /** 능력치 하나의 레벨과 그 레벨 안에서 쌓인 포인트. */
    public record Level(Long level, Long point) {
    }

    public record Counts(
            long problemCount,
            long solveCount,
            long correctSolveCount,
            long practiceNoteCount,
            long folderCount,
            long tagCount,
            long studyRoomCount,
            long achievementCount,
            long fcmTokenCount,
            long loginDayCount
    ) {
        public double correctRate() {
            return solveCount == 0 ? 0.0 : (double) correctSolveCount * 100 / solveCount;
        }
    }

    public record ProblemRow(
            Long problemId,
            String memo,
            String reference,
            String folderName,
            String analysisStatus,
            String subject,
            long solveCount,
            LocalDate nextReviewAt,
            LocalDateTime createdAt
    ) {
    }

    public record SolveRow(
            Long solveId,
            Long problemId,
            String problemReference,
            String answerStatus,
            Integer timeSpentSeconds,
            String reflection,
            String moodEmojiKey,
            LocalDateTime practicedAt
    ) {
    }

    public record PracticeNoteRow(
            Long practiceNoteId,
            String title,
            long problemCount,
            Long practiceCount,
            LocalDateTime lastSolvedAt,
            String repeatType,
            LocalDateTime createdAt
    ) {
    }

    public record FolderRow(
            Long folderId,
            String name,
            String parentName,
            long problemCount,
            LocalDateTime createdAt
    ) {
    }

    public record TagRow(Long tagId, String name, long problemCount) {
    }

    public record MissionProgressRow(
            String code,
            String title,
            String category,
            String periodKey,
            int currentValue,
            int targetValue,
            LocalDateTime completedAt,
            LocalDateTime claimedAt,
            String rewardType,
            Integer rewardValue
    ) {
    }

    public record MissionLogRow(
            Long missionLogId,
            String missionType,
            Long point,
            Long referenceId,
            LocalDateTime createdAt
    ) {
    }

    public record StudyRoomRow(
            Long roomId,
            String name,
            String role,
            long memberCount,
            Integer weeklyGoal,
            LocalDateTime joinedAt
    ) {
    }

    public record CosmeticRow(
            String slot,
            String slotName,
            String itemKey,
            String itemName,
            String setName,
            LocalDateTime updatedAt
    ) {
    }

    public record AchievementRow(
            String key,
            String name,
            String description,
            LocalDateTime earnedAt
    ) {
    }

    public record MoodRow(LocalDate studyDate, String emojiKey) {
    }
}
