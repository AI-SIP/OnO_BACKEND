package com.aisip.OnO.backend.admin.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 통계 화면과 관리자 홈이 쓰는 값 묶음.
 *
 * <p>화면 하나에서만 쓰는 작은 record 가 많아서 파일을 나누지 않고 한곳에 모았다.
 */
public final class AdminStatsDto {

    private AdminStatsDto() {
    }

    /** 화면 표기용. DB 에는 enum 이름이 그대로 들어 있다. */
    public static String analysisStatusLabel(String status) {
        if (status == null) {
            return "없음";
        }
        return switch (status) {
            case "COMPLETED" -> "완료";
            case "FAILED" -> "실패";
            case "PROCESSING" -> "분석 중";
            case "NOT_STARTED" -> "대기";
            case "NO_IMAGE" -> "이미지 없음";
            case "RATE_LIMIT_EXCEEDED" -> "요청 한도 초과";
            default -> status;
        };
    }

    public static String platformLabel(String platform) {
        if (platform == null) {
            return "알 수 없음";
        }
        return switch (platform.toUpperCase()) {
            case "GUEST" -> "게스트";
            case "GOOGLE" -> "구글";
            case "APPLE" -> "애플";
            case "KAKAO" -> "카카오";
            case "ADMIN" -> "관리자";
            default -> platform;
        };
    }

    public static String answerStatusLabel(String status) {
        if (status == null) {
            return "기록 없음";
        }
        return switch (status) {
            case "CORRECT" -> "정답";
            case "WRONG" -> "오답";
            case "PARTIAL" -> "부분 정답";
            default -> status;
        };
    }

    /** 이번 기간 값과 바로 앞 같은 길이 기간의 값. 증감률은 앞 기간이 0 이면 계산하지 않는다. */
    public record Metric(double value, double previous) {

        public Double deltaPercent() {
            if (previous == 0) {
                return null;
            }
            return (value - previous) * 100.0 / previous;
        }
    }

    /** 이름과 건수. 분포 막대에 쓴다. */
    public record LabelCount(String label, long count) {

        public double percentOf(long total) {
            return total == 0 ? 0.0 : count * 100.0 / total;
        }
    }

    /** 가입 코호트 중 N일째에 다시 들어온 비율. 아직 N일이 지나지 않은 가입자는 코호트에서 뺀다. */
    public record Retention(long cohort, long retained) {

        public double rate() {
            return cohort == 0 ? 0.0 : retained * 100.0 / cohort;
        }
    }

    public record DailyRow(
            LocalDate date,
            long activeUsers,
            long newUsers,
            long problems,
            long solves,
            long practiceNotes,
            long missionsCompleted,
            long roomActivity
    ) {
    }

    public record RankRow(Long userId, String name, String email, long count) {
    }

    public record UserStats(
            long totalUsers,
            long guestUsers,
            long notificationEnabledUsers,
            long fcmUsers,
            Metric signups,
            Metric activeUsers,
            Metric averageDau,
            long wau,
            long mau,
            double stickiness,
            Retention d1,
            Retention d7,
            List<LabelCount> signupsByPlatform
    ) {

        public double guestRate() {
            return totalUsers == 0 ? 0.0 : guestUsers * 100.0 / totalUsers;
        }

        public double notificationEnabledRate() {
            return totalUsers == 0 ? 0.0 : notificationEnabledUsers * 100.0 / totalUsers;
        }
    }

    public record LearningStats(
            long totalProblems,
            long totalSolves,
            long totalPracticeNotes,
            Metric problems,
            long problemWriters,
            Metric solves,
            long solvers,
            long correct,
            long wrong,
            long partial,
            Double averageSolveSeconds,
            long solvesWithReflection,
            Metric practiceNotes,
            long practiceNoteFirstCompletions,
            long tagsCreated,
            long foldersCreated,
            long calendarMoods,
            long solveMoods
    ) {

        public double problemsPerActiveUser(long activeUsers) {
            return activeUsers == 0 ? 0.0 : problems.value() / activeUsers;
        }

        public double correctRate() {
            long answered = correct + wrong + partial;
            return answered == 0 ? 0.0 : correct * 100.0 / answered;
        }

        public double reflectionRate() {
            return solves.value() == 0 ? 0.0 : solvesWithReflection * 100.0 / solves.value();
        }
    }

    public record AnalysisStats(
            List<LabelCount> allStatuses,
            List<LabelCount> periodStatuses,
            long allTotal,
            long periodTotal,
            List<LabelCount> subjects
    ) {

        public long periodCount(String status) {
            return periodStatuses.stream()
                    .filter(s -> s.label().equals(status))
                    .mapToLong(LabelCount::count)
                    .sum();
        }

        /** 끝난 분석(완료, 실패) 가운데 실패 비율. 처리 중이거나 대기인 건은 분모에 넣지 않는다. */
        public double periodFailureRate() {
            long completed = periodCount("COMPLETED");
            long failed = periodCount("FAILED");
            return completed + failed == 0 ? 0.0 : failed * 100.0 / (completed + failed);
        }
    }

    public record GrowthStats(
            long missionsCompleted,
            long missionsClaimed,
            List<LabelCount> topMissions,
            long achievementsEarned,
            List<LabelCount> topAchievements,
            long cosmeticUsers,
            List<LabelCount> levelDistribution
    ) {

        public long levelTotal() {
            return levelDistribution.stream().mapToLong(LabelCount::count).sum();
        }
    }

    public record RoomStats(
            long totalRooms,
            long roomsCreated,
            long membersJoined,
            long sharedProblems,
            long comments,
            long reactions,
            long challengesCreated,
            long challengesCompleted,
            long challengesFailed
    ) {
    }

    public record RecentUser(Long userId, String name, String email, String platform, LocalDateTime createdAt) {
    }

    public record RecentProblem(Long problemId, Long userId, String userName, String reference, String memo,
                                String analysisStatus, LocalDateTime createdAt) {
    }

    public record RecentSolve(Long solveId, Long problemId, Long userId, String userName, String answerStatus,
                              LocalDateTime practicedAt) {
    }

    public record RecentFeedback(Long feedbackId, Integer npsScore, String mostUsedFeature, String painPoints,
                                 LocalDateTime submittedAt) {
    }
}
