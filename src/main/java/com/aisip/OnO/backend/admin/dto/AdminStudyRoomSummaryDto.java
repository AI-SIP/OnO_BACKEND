package com.aisip.OnO.backend.admin.dto;

import com.aisip.OnO.backend.studyroom.entity.StudyRoom;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class AdminStudyRoomSummaryDto {

    private Long id;
    private String name;
    private long memberCount;
    private long sharedProblemCount;
    private LocalDateTime createdAt;

    public static AdminStudyRoomSummaryDto from(StudyRoom room, long memberCount, long sharedProblemCount) {
        return AdminStudyRoomSummaryDto.builder()
                .id(room.getId())
                .name(room.getName())
                .memberCount(memberCount)
                .sharedProblemCount(sharedProblemCount)
                .createdAt(room.getCreatedAt())
                .build();
    }
}
