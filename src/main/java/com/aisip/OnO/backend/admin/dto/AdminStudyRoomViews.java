package com.aisip.OnO.backend.admin.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 관리자 스터디룸 화면이 쓰는 조회 결과.
 *
 * <p>화면 하나에 쓰이는 작은 행이 많아서 파일을 나누지 않고 한곳에 모았다.
 * 전부 SQL 결과를 그대로 옮겨 담는 값이라 엔티티를 거치지 않는다.
 */
public final class AdminStudyRoomViews {

    private AdminStudyRoomViews() {
    }

    public record Overview(
            long totalRooms,
            long activeRoomsThisWeek,
            long totalMembers,
            long sharedProblems,
            long inProgressChallenges
    ) {
    }

    public record RoomRow(
            Long id,
            String name,
            String thumbnailUrl,
            Long hostUserId,
            String hostName,
            long memberCount,
            long sharedProblemCount,
            long commentCount,
            long inProgressChallengeCount,
            long completedChallengeCount,
            LocalDateTime lastActivityAt,
            LocalDateTime createdAt
    ) {
    }

    public record RoomHeader(
            Long id,
            String name,
            String thumbnailUrl,
            Long hostUserId,
            String hostName,
            LocalDateTime createdAt
    ) {
    }

    public record InviteCode(String code, LocalDateTime createdAt, LocalDateTime expiredAt, boolean expired) {
    }

    public record Member(
            Long userId,
            String userName,
            String email,
            boolean host,
            Integer weeklyGoal,
            LocalDateTime joinedAt,
            long sharedProblemCount
    ) {
    }

    public record Challenge(
            Long id,
            String title,
            String typeLabel,
            String metricLabel,
            String periodLabel,
            Integer targetValue,
            String status,
            String statusLabel,
            LocalDateTime startAt,
            LocalDateTime endAt,
            LocalDateTime completedAt,
            String createdByName
    ) {
    }

    public record SharedProblem(
            Long id,
            Long problemId,
            Long sharedByUserId,
            String sharedByName,
            String comment,
            long reactionCount,
            long commentCount,
            LocalDateTime sharedAt,
            List<Comment> comments
    ) {
        public SharedProblem withComments(List<Comment> comments) {
            return new SharedProblem(id, problemId, sharedByUserId, sharedByName, comment,
                    reactionCount, commentCount, sharedAt, comments);
        }
    }

    public record Comment(
            Long sharedProblemId,
            Long authorId,
            String authorName,
            String content,
            long reactionCount,
            LocalDateTime createdAt
    ) {
    }

    public record Feed(
            Long id,
            String eventLabel,
            Long userId,
            String userName,
            String summary,
            long reactionCount,
            LocalDateTime createdAt
    ) {
    }

    public record WeeklyReport(
            LocalDate weekStart,
            LocalDate weekEnd,
            String topMemberName,
            Integer topMemberProblemCount,
            String longestStreakName,
            Integer longestStreakDays,
            Integer totalProblems,
            Integer challengesCompleted,
            String cheerMessage,
            long readCount
    ) {
    }

    public record Detail(
            RoomHeader room,
            List<InviteCode> inviteCodes,
            List<Member> members,
            List<Challenge> challenges,
            List<SharedProblem> sharedProblems,
            List<Feed> feeds,
            List<WeeklyReport> weeklyReports,
            long totalCommentCount,
            long totalReactionCount
    ) {
        public long inProgressChallengeCount() {
            return challenges.stream().filter(c -> "IN_PROGRESS".equals(c.status())).count();
        }

        public long completedChallengeCount() {
            return challenges.stream().filter(c -> "COMPLETED".equals(c.status())).count();
        }
    }
}
