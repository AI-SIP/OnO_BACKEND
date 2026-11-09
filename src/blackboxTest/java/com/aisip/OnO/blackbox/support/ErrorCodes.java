package com.aisip.OnO.blackbox.support;

/**
 * 앱이 내려주는 errorCode. 앱 클래스를 import 할 수 없어서 숫자를 그대로 옮겨 둔다.
 * 숫자가 바뀌면 출시된 앱의 분기가 깨지므로, 여기서 어긋나면 그 자체가 계약 위반이다.
 */
public final class ErrorCodes {

    private ErrorCodes() {
    }

    // 인증 (AuthErrorCase)
    public static final int INVALID_REFRESH_TOKEN = 1001;
    public static final int REFRESH_TOKEN_NOT_FOUND = 1002;
    public static final int AUTHENTICATION_FAILED = 1007;
    public static final int ACCESS_DENIED = 1008;
    public static final int INVALID_ACCESS_TOKEN = 1009;
    public static final int SOCIAL_TOKEN_REQUIRED = 1010;
    public static final int INVALID_SOCIAL_TOKEN = 1011;
    public static final int SOCIAL_IDENTIFIER_MISMATCH = 1012;
    public static final int SOCIAL_VERIFICATION_UNAVAILABLE = 1013;

    // 파일 업로드 (FileUploadErrorCase)
    public static final int FILE_NOT_FOUND = 2002;
    public static final int INVALID_IMAGE_FILE = 2003;
    public static final int IMAGE_USER_UNMATCHED = 2006;

    // 오답노트 (ProblemErrorCase)
    public static final int PROBLEM_NOT_FOUND = 4001;
    public static final int PROBLEM_USER_UNMATCHED = 4002;
    public static final int ANALYSIS_RATE_LIMIT_EXCEEDED = 4005;
    public static final int PROBLEM_MEMO_TOO_LONG = 4006;
    public static final int PROBLEM_FOLDER_ID_REQUIRED = 4008;

    // 풀이 기록 (ProblemSolveErrorCase)
    public static final int PROBLEM_SOLVE_NOT_FOUND = 4021;
    public static final int PROBLEM_SOLVE_USER_UNMATCHED = 4022;

    // 폴더 (FolderErrorCase)
    public static final int FOLDER_NOT_FOUND = 5001;
    public static final int FOLDER_USER_UNMATCHED = 5002;
    public static final int ROOT_FOLDER_CANNOT_REMOVE = 5004;
    public static final int ROOT_FOLDER_CANNOT_UPDATE = 5005;
    public static final int INVALID_PARENT_FOLDER = 5006;

    // 복습노트 (PracticeNoteErrorCase)
    public static final int PRACTICE_NOTE_NOT_FOUND = 6001;
    public static final int PRACTICE_NOTE_USER_UNMATCHED = 6002;
    public static final int PRACTICE_NOTIFICATION_WEEK_DAYS_REQUIRED = 6003;

    // 미션 (MissionErrorCase)
    public static final int MISSION_PROGRESS_NOT_FOUND = 7010;
    public static final int MISSION_NOT_COMPLETED = 7011;
    public static final int MISSION_ALREADY_CLAIMED = 7012;

    // 태그 (TagErrorCase)
    public static final int TAG_NAME_EMPTY = 9001;
    public static final int TAG_NAME_TOO_LONG = 9002;
    public static final int TAG_NOT_FOUND = 9003;
    public static final int TAG_USER_UNMATCHED = 9004;

    // 스터디룸 (StudyRoomErrorCase)
    public static final int STUDY_ROOM_NOT_FOUND = 10001;
    public static final int STUDY_ROOM_FORBIDDEN = 10002;
    public static final int STUDY_ROOM_HOST_ONLY = 10003;
    public static final int INVITE_CODE_INVALID = 10006;
    public static final int ALREADY_MEMBER = 10008;
    public static final int CHALLENGE_NOT_FOUND = 10009;
    public static final int SHARED_PROBLEM_NOT_FOUND = 10013;
    public static final int SHARED_PROBLEM_COMMENT_FORBIDDEN = 10019;
    public static final int ALREADY_SHARED_PROBLEM = 10020;

    // 꾸미기, 이모지, 캘린더, 공지
    public static final int INVALID_EMOJI_KEY = 11001;
    public static final int INVALID_DATE_FORMAT = 12002;
    public static final int NOTICE_NOT_FOUND = 14001;
    public static final int COSMETIC_ITEM_NOT_FOUND = 15002;
    public static final int COSMETIC_ITEM_NOT_OWNED = 15003;
}
