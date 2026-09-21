package com.aisip.OnO.backend.admin.dto;

import com.aisip.OnO.backend.problemsolve.entity.ImprovementType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 관리자 학습 기록 화면(오답노트, 복습 기록, 복습노트)이 쓰는 조회 결과.
 *
 * <p>화면 전용 모양이라 한 파일에 모아 둔다. 도메인 DTO 를 고치지 않고 관리자 화면에 필요한
 * 열만 SQL 로 바로 채운다.
 */
public final class AdminLearningDto {

    private AdminLearningDto() {
    }

    public record ProblemRow(
            Long problemId,
            Long userId,
            String userName,
            String userEmail,
            Long folderId,
            String folderName,
            String reference,
            String memo,
            String analysisStatus,
            String subject,
            String problemType,
            long solveCount,
            long tagCount,
            LocalDateTime createdAt
    ) {
    }

    public record ProblemSummary(long total, long today, long failed, long rateLimited, long processing) {
    }

    public record ProblemDetail(
            Long problemId,
            Long userId,
            String userName,
            String userEmail,
            boolean userDeleted,
            Long folderId,
            String folderName,
            String reference,
            String memo,
            LocalDateTime createdAt,
            LocalDateTime solvedAt,
            LocalDate nextReviewAt,
            Integer reviewInterval,
            Integer consecutiveCorrectCount,
            String analysisStatus,
            String subject,
            String problemType,
            String keyPoints,
            String solution,
            String commonMistakes,
            String studyTips,
            String errorMessage
    ) {
    }

    public record ImageRow(String url, String type) {
    }

    public record TagRow(Long tagId, String name) {
    }

    public record NoteRef(Long noteId, String title, long practiceCount, LocalDateTime lastSolvedAt) {
    }

    public record SharedRoomRow(
            Long sharedProblemId,
            Long roomId,
            String roomName,
            Long sharedByUserId,
            String sharedByName,
            String comment,
            long commentCount,
            long reactionCount,
            LocalDateTime sharedAt
    ) {
    }

    public record SolveRow(
            Long solveId,
            LocalDateTime practicedAt,
            Long userId,
            String userName,
            String userEmail,
            Long problemId,
            String problemReference,
            String problemMemo,
            String answerStatus,
            Integer timeSpentSeconds,
            String reflection,
            String improvements,
            String moodEmojiKey,
            boolean migratedFromLegacy,
            long imageCount,
            List<String> imageUrls
    ) {
        public SolveRow withImages(List<String> urls) {
            return new SolveRow(solveId, practicedAt, userId, userName, userEmail, problemId, problemReference,
                    problemMemo, answerStatus, timeSpentSeconds, reflection, improvements, moodEmojiKey,
                    migratedFromLegacy, imageCount, List.copyOf(urls));
        }

        /** 소요 시간을 "3분 20초" 처럼 보여 준다. 기록하지 않았으면 null. */
        public String timeSpentText() {
            return AdminLearningDto.durationText(timeSpentSeconds);
        }

        /** improvements 는 ["FASTER_SOLVING", ...] 꼴의 JSON 배열 문자열이다. 모르는 값은 그대로 보여 준다. */
        public List<String> improvementTexts() {
            if (improvements == null || improvements.isBlank()) {
                return List.of();
            }
            return java.util.Arrays.stream(improvements.replaceAll("[\\[\\]\"]", "").split(","))
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .map(value -> {
                        try {
                            return ImprovementType.valueOf(value).getDescription();
                        } catch (IllegalArgumentException e) {
                            return value;
                        }
                    })
                    .toList();
        }
    }

    public record SolveSummary(
            long total,
            long correct,
            long wrong,
            long partial,
            long unknown,
            Double averageTimeSeconds,
            long withReflection
    ) {
        /** 결과를 고르지 않은 레거시 이관분(UNKNOWN)은 분모에서 뺀다. */
        public double correctRate() {
            long judged = correct + wrong + partial;
            return judged == 0 ? 0.0 : (double) correct * 100 / judged;
        }

        public String averageTimeText() {
            return averageTimeSeconds == null ? null : AdminLearningDto.durationText((int) Math.round(averageTimeSeconds));
        }
    }

    public record NoteRow(
            Long noteId,
            String title,
            Long userId,
            String userName,
            String userEmail,
            long problemCount,
            long practiceCount,
            LocalDateTime lastSolvedAt,
            String lastMoodEmojiKey,
            String repeatType,
            Integer intervalDays,
            Integer hour,
            Integer minute,
            List<Integer> weekDays,
            LocalDateTime createdAt
    ) {
        public NoteRow withWeekDays(List<Integer> days) {
            return new NoteRow(noteId, title, userId, userName, userEmail, problemCount, practiceCount,
                    lastSolvedAt, lastMoodEmojiKey, repeatType, intervalDays, hour, minute,
                    days == null ? List.of() : List.copyOf(days), createdAt);
        }

        public boolean hasNotification() {
            return hour != null && minute != null;
        }

        /** "매주 월, 수 21:00" 처럼 알림 설정을 한 줄로 요약한다. */
        public String notificationText() {
            if (!hasNotification()) {
                return null;
            }
            String time = String.format("%02d:%02d", hour, minute);
            if ("weekly".equalsIgnoreCase(repeatType) && weekDays != null && !weekDays.isEmpty()) {
                String days = weekDays.stream().sorted().map(AdminLearningDto::weekDayText).collect(Collectors.joining(", "));
                return "매주 " + days + " " + time;
            }
            if (intervalDays != null && intervalDays > 1 && !"daily".equalsIgnoreCase(repeatType)) {
                return intervalDays + "일마다 " + time;
            }
            return "매일 " + time;
        }
    }

    public record NoteSummary(long total, long withNotification, long practiced, long totalPracticeCount) {
    }

    public record NoteProblemRow(
            Long problemId,
            String reference,
            String memo,
            String subject,
            long solveCount,
            String lastAnswerStatus,
            LocalDateTime lastPracticedAt,
            LocalDateTime createdAt
    ) {
    }

    static String durationText(Integer seconds) {
        if (seconds == null) {
            return null;
        }
        if (seconds < 60) {
            return seconds + "초";
        }
        int minutes = seconds / 60;
        int rest = seconds % 60;
        if (minutes >= 60) {
            return (minutes / 60) + "시간 " + (minutes % 60) + "분";
        }
        return rest == 0 ? minutes + "분" : minutes + "분 " + rest + "초";
    }

    static String weekDayText(int day) {
        return switch (day) {
            case 1 -> "월";
            case 2 -> "화";
            case 3 -> "수";
            case 4 -> "목";
            case 5 -> "금";
            case 6 -> "토";
            case 7 -> "일";
            default -> String.valueOf(day);
        };
    }
}
