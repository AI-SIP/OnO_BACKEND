package com.aisip.OnO.backend.mcp.dto;

import com.aisip.OnO.backend.admin.dto.AdminLearningDto.ProblemSummary;
import com.aisip.OnO.backend.admin.dto.AdminStudyRoomViews.Overview;
import com.aisip.OnO.backend.admin.dto.AdminUserRows.Counts;
import com.aisip.OnO.backend.admin.dto.AdminUserRows.Level;
import com.aisip.OnO.backend.admin.dto.AdminUserRows.Summary;
import com.aisip.OnO.backend.mcp.tool.McpPage;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 사용자, 문제, 피드백, 스터디룸 도구의 응답.
 *
 * <p>MCP 응답은 외부 AI 대화에 그대로 남는다. 그래서 관리자 화면용 레코드를 재사용하지 않고, 내보낼 필드만
 * 골라 담는다. 관리자 레코드에 필드가 새로 생겨도 여기에 적지 않으면 나가지 않는다.
 * 빠지는 것: 이름, 이메일, 프로필과 문제 이미지 URL, 메모, 출처, 폴더와 태그와 방 이름, 풀이 회고,
 * 댓글과 피드 본문, 초대 코드, 피드백 IP.
 */
public final class McpAdminDtos {

    private McpAdminDtos() {
    }

    // ─────────────────────────── 사용자 ───────────────────────────

    public record UserSearchResult(Summary summary, McpPage<UserRow> users) {
    }

    public record UserRow(
            Long userId,
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

    public record UserActivity(
            Long userId,
            String platform,
            boolean notificationEnabled,
            LocalDateTime lastActiveAt,
            LocalDate lastNotifiedAt,
            LocalDateTime createdAt,
            Levels levels,
            Counts counts,
            List<LocalDate> recentLoginDates,
            List<SolveRow> recentSolves,
            List<MissionLogRow> recentMissionLogs,
            List<StudyRoomMembership> studyRooms
    ) {
    }

    public record Levels(Level attendance, Level noteWrite, Level problemPractice, Level notePractice, Level totalStudy) {
    }

    public record SolveRow(
            Long solveId,
            Long problemId,
            String answerStatus,
            Integer timeSpentSeconds,
            String moodEmojiKey,
            LocalDateTime practicedAt
    ) {
    }

    public record MissionLogRow(String missionType, Long point, LocalDateTime createdAt) {
    }

    public record StudyRoomMembership(Long roomId, String role, long memberCount, Integer weeklyGoal, LocalDateTime joinedAt) {
    }

    // ─────────────────────────── 문제 ───────────────────────────

    public record ProblemList(ProblemSummary summary, McpPage<ProblemRow> problems) {
    }

    public record ProblemRow(
            Long problemId,
            Long userId,
            Long folderId,
            String analysisStatus,
            String subject,
            String problemType,
            long solveCount,
            long tagCount,
            LocalDateTime createdAt
    ) {
    }

    // ─────────────────────────── 피드백 ───────────────────────────

    public record FeedbackList(long totalCount, Double averageNps, McpPage<Feedback> feedbacks) {
    }

    public record Feedback(
            Long id,
            Integer npsScore,
            String usagePurpose,
            String usageFrequency,
            String mostUsedFeature,
            String painPoints,
            String desiredFeatures,
            String registrationPainPoints,
            String classificationMethod,
            Integer templateSatisfaction,
            Boolean practiceNoteUsed,
            String notificationEffectiveness,
            String reviewSetNonUsageReason,
            String studyRoomUsage,
            String studyRoomNonUsageReason,
            Integer challengeMotivation,
            Integer problemSharingUsefulness,
            LocalDateTime submittedAt
    ) {
    }

    // ─────────────────────────── 스터디룸 ───────────────────────────

    public record StudyRoomList(Overview overview, McpPage<StudyRoomRow> rooms) {
    }

    public record StudyRoomRow(
            Long roomId,
            Long hostUserId,
            long memberCount,
            long sharedProblemCount,
            long commentCount,
            long inProgressChallengeCount,
            long completedChallengeCount,
            LocalDateTime lastActivityAt,
            LocalDateTime createdAt
    ) {
    }

    public record StudyRoomDetail(
            Long roomId,
            Long hostUserId,
            LocalDateTime createdAt,
            long reactionCount,
            List<Member> members,
            List<Challenge> challenges,
            List<SharedProblem> sharedProblems,
            List<Feed> recentFeeds,
            List<WeeklyReport> weeklyReports
    ) {
    }

    public record Member(Long userId, boolean host, Integer weeklyGoal, LocalDateTime joinedAt, long sharedProblemCount) {
    }

    public record Challenge(
            Long challengeId,
            String type,
            String metric,
            String period,
            Integer targetValue,
            String status,
            LocalDateTime startAt,
            LocalDateTime endAt,
            LocalDateTime completedAt
    ) {
    }

    public record SharedProblem(Long problemId, Long sharedByUserId, long reactionCount, long commentCount, LocalDateTime sharedAt) {
    }

    public record Feed(String event, Long userId, long reactionCount, LocalDateTime createdAt) {
    }

    public record WeeklyReport(
            LocalDate weekStart,
            LocalDate weekEnd,
            Integer topMemberProblemCount,
            Integer longestStreakDays,
            Integer totalProblems,
            Integer challengesCompleted,
            long readCount
    ) {
    }
}
