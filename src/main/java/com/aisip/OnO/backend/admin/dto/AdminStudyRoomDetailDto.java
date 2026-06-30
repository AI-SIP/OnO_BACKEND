package com.aisip.OnO.backend.admin.dto;

import com.aisip.OnO.backend.studyroom.entity.StudyRoom;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomChallenge;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomChallengeStatus;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomMember;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class AdminStudyRoomDetailDto {

    private Long id;
    private String name;
    private Long hostUserId;
    private LocalDateTime createdAt;
    private long sharedProblemCount;
    private List<MemberInfo> members;
    private List<ChallengeInfo> challenges;

    @Getter
    @Builder
    public static class MemberInfo {
        private Long userId;
        private String userName;
        private String role;      // 방장 / 멤버
        private LocalDateTime joinedAt;
    }

    @Getter
    @Builder
    public static class ChallengeInfo {
        private Long id;
        private String title;
        private String type;      // 개인 / 그룹 / 연속
        private String metric;
        private String status;    // 진행 중 / 완료 / 실패 / 만료
        private Integer targetValue;
        private LocalDateTime startAt;
        private LocalDateTime endAt;
        private LocalDateTime completedAt;
        private boolean isActive;
    }

    public static AdminStudyRoomDetailDto from(StudyRoom room,
                                               List<StudyRoomMember> members,
                                               List<StudyRoomChallenge> challenges,
                                               long sharedProblemCount) {
        return AdminStudyRoomDetailDto.builder()
                .id(room.getId())
                .name(room.getName())
                .hostUserId(room.getHostUserId())
                .createdAt(room.getCreatedAt())
                .sharedProblemCount(sharedProblemCount)
                .members(members.stream()
                        .map(m -> MemberInfo.builder()
                                .userId(m.getUser().getId())
                                .userName(m.getUser().getName())
                                .role(m.getRole().name().equals("HOST") ? "방장" : "멤버")
                                .joinedAt(m.getCreatedAt())
                                .build())
                        .toList())
                .challenges(challenges.stream()
                        .map(c -> ChallengeInfo.builder()
                                .id(c.getId())
                                .title(c.getTitle())
                                .type(translateType(c))
                                .metric(c.getMetric().name())
                                .status(translateStatus(c.getStatus()))
                                .targetValue(c.getTargetValue())
                                .startAt(c.getStartAt())
                                .endAt(c.getEndAt())
                                .completedAt(c.getCompletedAt())
                                .isActive(c.getStatus() == StudyRoomChallengeStatus.IN_PROGRESS)
                                .build())
                        .toList())
                .build();
    }

    private static String translateType(StudyRoomChallenge c) {
        return switch (c.getType()) {
            case INDIVIDUAL -> "개인";
            case GROUP      -> "그룹";
            case STREAK     -> "연속";
        };
    }

    private static String translateStatus(StudyRoomChallengeStatus s) {
        return switch (s) {
            case IN_PROGRESS -> "진행 중";
            case COMPLETED   -> "완료";
            case FAILED      -> "실패";
            case EXPIRED     -> "만료";
        };
    }
}
