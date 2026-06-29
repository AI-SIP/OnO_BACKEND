package com.aisip.OnO.backend.studyroom.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class StudyRoomDtos {

    private StudyRoomDtos() {
    }

    public record StudyRoomCreateRequest(String name) {
    }

    public record StudyRoomUpdateRequest(String name) {
    }

    public record StudyRoomJoinRequest(String code) {
    }

    public record StudyRoomGoalUpdateRequest(Integer weeklyGoal) {
    }

    public record ReactionToggleRequest(String emoji) {
    }

    public record ChallengeCreateRequest(String title, String type, String metric, String period,
                                         Integer periodDays, Integer targetValue,
                                         LocalDateTime startAt, LocalDateTime endAt) {
    }

    public record SharedProblemCreateRequest(Long problemId, String comment) {
    }

    public record SharedProblemCommentRequest(String content) {
    }

    public record StudyRoomListResponse(Long roomId, String name, Long hostUserId, int memberCount,
                                        String thumbnailUrl, boolean hasUnreadReport,
                                        int todayPracticeMemberCount, int todayPracticeCount) {
    }

    public record StudyRoomDetailResponse(Long roomId, String name, Long hostUserId, String thumbnailUrl,
                                          int memberCount, List<StudyRoomMemberResponse> members) {
    }

    public record StudyRoomMemberResponse(Long userId, String name, String profileImageUrl,
                                          Long totalStudyLevel, int currentStreak,
                                          int weeklyProblemCount, int weeklyPracticeCount,
                                          Integer weeklyGoal, Integer goalProgress,
                                          int todayPracticeCount, boolean practicedToday) {
    }

    public record StudyRoomThumbnailUpdateResponse(String thumbnailUrl) {
    }

    public record InviteCodeResponse(String code, LocalDateTime expiredAt) {
    }

    public record GoalUpdateResponse(Integer weeklyGoal, Integer goalProgress) {
    }

    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, boolean hasNext) {
    }

    public record ReactionResponse(String emoji, long count, boolean reactedByMe) {
    }

    public record FeedItemResponse(Long feedId, Long userId, String userName, String userProfileImageUrl, String eventType,
                                   Map<String, Object> metadata, LocalDateTime createdAt,
                                   List<ReactionResponse> reactions) {
    }

    public record FeedReactionToggleResponse(Long feedId, List<ReactionResponse> reactions) {
    }

    public record ChallengeResponse(Long challengeId, String title, String type, String metric, String period,
                                    Integer periodDays, Integer targetValue,
                                    LocalDateTime startAt, LocalDateTime endAt, String status,
                                    List<ChallengeMemberProgressResponse> memberProgress,
                                    Integer groupCurrent) {
    }

    public record ChallengeMemberProgressResponse(Long userId, String name, int current, boolean cleared) {
    }

    public record ActiveStudySessionsResponse(List<ActiveStudySessionResponse> activeSessions) {
    }

    public record ActiveStudySessionResponse(Long userId, String name, LocalDateTime startedAt) {
    }

    public record StudySessionStartResponse(Long sessionId, LocalDateTime startedAt) {
    }

    public record StudySessionEndResponse(Long sessionId, LocalDateTime startedAt, LocalDateTime endedAt,
                                          Integer durationMinutes) {
    }

    public record SharedProblemResponse(Long sharedProblemId, Long sharedByUserId, String sharedByName,
                                        String sharedByProfileImageUrl,
                                        Long problemId, String problemImageUrl, List<String> problemImageUrls, String reference,
                                        String comment, long commentCount, LocalDateTime sharedAt,
                                        List<ReactionResponse> reactions) {
    }

    public record SharedProblemReactionToggleResponse(Long sharedProblemId, List<ReactionResponse> reactions) {
    }

    public record SharedProblemCommentReactionToggleResponse(Long commentId, List<ReactionResponse> reactions) {
    }

    public record SharedProblemCommentResponse(Long commentId, String content, Long authorId, String authorName,
                                               String authorProfileImageUrl, LocalDateTime createdAt,
                                               LocalDateTime updatedAt, boolean isEdited, boolean isMine,
                                               boolean canDelete, List<ReactionResponse> reactions) {
    }

    public record WeeklyReportResponse(Long reportId, LocalDate weekStart, LocalDate weekEnd,
                                       String topMemberName, Integer topMemberProblemCount,
                                       String longestStreakName, Integer longestStreakDays,
                                       Integer totalProblems, Integer challengesCompleted,
                                       String cheerMessage, boolean isRead) {
    }

    public record WeeklyReportReadResponse(Long reportId, boolean isRead) {
    }
}
