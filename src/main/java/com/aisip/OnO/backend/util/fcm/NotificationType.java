package com.aisip.OnO.backend.util.fcm;

/**
 * 푸시 알림 데이터의 {@code type} 값.
 *
 * <p>앱은 {@code data["type"]} 하나만 보고 어떤 화면을 열지 고른다. 값이 빠지거나 오타가 나면
 * 알림을 눌러도 아무 화면이 열리지 않으므로, 발송하는 쪽에서 문자열을 직접 쓰지 말고 여기 상수를 쓴다.
 *
 * <p>표기는 소문자 스네이크로 통일한다. 값은 앱과 맞춰 둔 계약이라
 * 바꾸려면 프론트 분기도 같이 움직여야 한다.
 */
public final class NotificationType {

    /** 오늘 복습할 문제가 있는 사용자에게 보내는 일일 알림. */
    public static final String REVIEW_DUE = "review_due";

    /** 며칠 접속하지 않은 사용자에게 보내는 재참여 알림. */
    public static final String REENGAGEMENT = "reengagement";

    /** 30일 넘게 접속하지 않은 사용자에게 월 1회 보내는 재참여 알림. */
    public static final String REENGAGEMENT_MONTHLY = "reengagement_monthly";

    /** 문제 단위 복습 리마인더. */
    public static final String PROBLEM_REVIEW_REMINDER = "problem_review_reminder";

    /** 사용자가 복습노트에 직접 걸어 둔 반복 알림. */
    public static final String PRACTICE_NOTE_REMINDER = "practice_note_reminder";

    /** 챌린지 중간/마감 하루 전 알림. */
    public static final String CHALLENGE_NOTIFICATION = "challenge_notification";

    /** 챌린지 달성 알림. */
    public static final String CHALLENGE_COMPLETED = "challenge_completed";

    /** 스터디룸에 문제가 새로 공유됐을 때. */
    public static final String SHARED_PROBLEM = "shared_problem";

    /** 내가 공유한 문제에 반응이 달렸을 때. */
    public static final String SHARED_PROBLEM_REACTION = "shared_problem_reaction";

    /** 내가 공유한 문제에 댓글이 달렸을 때. */
    public static final String SHARED_PROBLEM_COMMENT = "shared_problem_comment";

    private NotificationType() {
    }
}
