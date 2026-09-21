package com.aisip.OnO.backend.admin.repository;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * 스터디룸 enum 값을 관리자 화면에 보일 한국어로 바꾼다.
 *
 * <p>DB 에서 문자열로 바로 읽기 때문에 enum 에 없는 값(예전 배포에서 지운 상수)이 올 수 있다.
 * 그때 예외를 던지지 않고 원래 값을 그대로 보여 준다.
 */
final class AdminStudyRoomLabels {

    private AdminStudyRoomLabels() {
    }

    static String challengeType(String type) {
        if (type == null) return "-";
        return switch (type) {
            case "INDIVIDUAL" -> "개인";
            case "GROUP" -> "그룹";
            case "STREAK" -> "연속";
            default -> type;
        };
    }

    static String challengeMetric(String metric) {
        if (metric == null) return "-";
        return switch (metric) {
            case "PROBLEM_COUNT" -> "오답노트 등록";
            case "PRACTICE_COUNT" -> "복습";
            case "ATTENDANCE" -> "출석";
            case "WEEKLY_PROBLEM_COUNT" -> "주간 오답노트 (구)";
            case "WEEKLY_PRACTICE_COUNT" -> "주간 복습 (구)";
            case "STREAK" -> "연속 기록 (구)";
            default -> metric;
        };
    }

    static String challengePeriod(String period, Integer periodDays) {
        if (periodDays != null && periodDays > 0) {
            return periodDays + "일";
        }
        if (period == null) return "-";
        return switch (period) {
            case "DAILY" -> "하루";
            case "WEEKLY" -> "일주일";
            case "MONTHLY" -> "한 달";
            default -> period;
        };
    }

    static String challengeStatus(String status) {
        if (status == null) return "-";
        return switch (status) {
            case "IN_PROGRESS" -> "진행 중";
            case "COMPLETED" -> "완료";
            case "FAILED" -> "실패";
            case "EXPIRED" -> "만료";
            default -> status;
        };
    }

    static String feedEvent(String eventType) {
        if (eventType == null) return "-";
        return switch (eventType) {
            case "PROBLEM_REGISTERED" -> "오답노트 등록";
            case "PRACTICE_COMPLETED" -> "복습 완료";
            case "STREAK_MILESTONE" -> "연속 출석";
            case "LEVEL_UP" -> "레벨 업";
            case "CHALLENGE_CLEARED" -> "챌린지 달성";
            case "PROBLEM_SHARED" -> "문제 공유";
            default -> eventType;
        };
    }

    static String ability(Object ability) {
        if (ability == null) return "총 학습";
        return switch (String.valueOf(ability)) {
            case "ATTENDANCE" -> "출석";
            case "NOTE_WRITE" -> "오답노트 작성";
            case "PROBLEM_PRACTICE" -> "문제 복습";
            case "NOTE_PRACTICE" -> "복습노트";
            default -> String.valueOf(ability);
        };
    }

    /** 피드 메타데이터를 한 줄로 줄인다. 모르는 모양이면 키와 값을 그대로 늘어놓는다. */
    static String feedSummary(String eventType, Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return "";
        }
        Object count = metadata.get("count");
        switch (eventType == null ? "" : eventType) {
            case "PROBLEM_REGISTERED":
                if (count != null) {
                    Object subject = metadata.get("subject");
                    return count + "개" + (subject == null ? "" : " (" + subject + ")");
                }
                break;
            case "PRACTICE_COMPLETED":
                if (count != null) return count + "회";
                break;
            case "STREAK_MILESTONE":
                if (metadata.get("days") != null) return metadata.get("days") + "일째";
                break;
            case "LEVEL_UP":
                if (metadata.get("level") != null) {
                    return ability(metadata.get("ability")) + " Lv." + metadata.get("level");
                }
                break;
            default:
                break;
        }
        return metadata.entrySet().stream()
                .map(e -> e.getKey() + " " + e.getValue())
                .collect(Collectors.joining(", "));
    }
}
