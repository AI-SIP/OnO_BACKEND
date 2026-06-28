package com.aisip.OnO.backend.studyroom.exception;

import com.aisip.OnO.backend.common.exception.ErrorCase;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum StudyRoomErrorCase implements ErrorCase {

    STUDY_ROOM_NOT_FOUND(404, 10001, "스터디룸을 찾을 수 없습니다."),
    STUDY_ROOM_FORBIDDEN(403, 10002, "스터디룸 멤버만 접근할 수 있습니다."),
    STUDY_ROOM_HOST_ONLY(403, 10003, "스터디룸 방장만 사용할 수 있습니다."),
    STUDY_ROOM_FULL(409, 10004, "스터디룸 최대 인원을 초과했습니다."),
    STUDY_ROOM_LIMIT_EXCEEDED(409, 10005, "참여 가능한 스터디룸 수를 초과했습니다."),
    INVITE_CODE_INVALID(400, 10006, "초대 코드가 유효하지 않습니다."),
    INVITE_CODE_EXPIRED(400, 10007, "초대 코드가 만료되었습니다."),
    ALREADY_MEMBER(409, 10008, "이미 참여 중인 스터디룸입니다."),
    CHALLENGE_NOT_FOUND(404, 10009, "챌린지를 찾을 수 없습니다."),
    CHALLENGE_LIMIT_EXCEEDED(409, 10010, "진행 중인 챌린지 수를 초과했습니다."),
    SESSION_ALREADY_ACTIVE(409, 10011, "이미 진행 중인 공부 세션이 있습니다."),
    SESSION_NOT_FOUND(404, 10012, "공부 세션을 찾을 수 없습니다."),
    SHARED_PROBLEM_NOT_FOUND(404, 10013, "공유 문제를 찾을 수 없습니다."),
    REPORT_NOT_FOUND(404, 10014, "주간 리포트를 찾을 수 없습니다."),
    INVALID_REACTION_EMOJI(400, 10015, "허용되지 않은 반응입니다."),
    INVALID_STUDY_ROOM_REQUEST(400, 10016, "스터디룸 요청 값이 올바르지 않습니다."),
    SHARED_PROBLEM_COMMENT_NOT_FOUND(404, 10017, "공유 문제 댓글을 찾을 수 없습니다.");

    private final Integer httpStatusCode;
    private final Integer errorCode;
    private final String message;
}
