package com.aisip.OnO.backend.studyroom.service;

import com.aisip.OnO.backend.mission.entity.UserMissionStatus;
import com.aisip.OnO.backend.studyroom.dto.StudyRoomDtos.*;
import com.aisip.OnO.backend.studyroom.dto.StudyRoomStats;
import com.aisip.OnO.backend.studyroom.dto.StudyRoomTodayPracticeSummary;
import com.aisip.OnO.backend.studyroom.entity.StudyRoom;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomMember;
import com.aisip.OnO.backend.studyroom.repository.StudyRoomWeeklyReportReadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class StudyRoomMapper {

    private final StudyRoomWeeklyReportReadRepository reportReadRepository;

    public StudyRoomListResponse toListResponse(StudyRoomMember member, int memberCount,
                                                StudyRoomTodayPracticeSummary todayPracticeSummary) {
        StudyRoom room = member.getRoom();
        return new StudyRoomListResponse(
                room.getId(),
                room.getName(),
                room.getHostUserId(),
                memberCount,
                room.getThumbnailUrl(),
                reportReadRepository.existsUnreadReport(room.getId(), member.getUser().getId()),
                todayPracticeSummary.todayPracticeMemberCount(),
                todayPracticeSummary.todayPracticeCount()
        );
    }

    public StudyRoomDetailResponse toDetailResponse(StudyRoom room, List<StudyRoomMember> members,
                                                    Map<Long, StudyRoomStats> statsByUserId,
                                                    Map<Long, Integer> todayPracticeCountsByUserId) {
        List<StudyRoomMemberResponse> memberResponses = members.stream()
                .map(member -> toMemberResponse(
                        member,
                        statsByUserId.get(member.getUser().getId()),
                        todayPracticeCountsByUserId.getOrDefault(member.getUser().getId(), 0)
                ))
                .toList();
        return new StudyRoomDetailResponse(
                room.getId(),
                room.getName(),
                room.getHostUserId(),
                room.getThumbnailUrl(),
                memberResponses.size(),
                memberResponses
        );
    }

    public StudyRoomMemberResponse toMemberResponse(StudyRoomMember member, StudyRoomStats stats, int todayPracticeCount) {
        int weeklyProblemCount = stats == null ? 0 : stats.weeklyProblemCount();
        int weeklyPracticeCount = stats == null ? 0 : stats.weeklyPracticeCount();
        int currentStreak = stats == null ? 0 : stats.currentStreak();
        Integer goalProgress = member.getWeeklyGoal() == null ? null : weeklyProblemCount;
        UserMissionStatus missionStatus = member.getUser().getUserMissionStatus();
        Long totalStudyLevel = missionStatus == null ? 1L : missionStatus.getTotalStudyLevel();
        return new StudyRoomMemberResponse(
                member.getUser().getId(),
                member.getUser().getName(),
                totalStudyLevel,
                currentStreak,
                weeklyProblemCount,
                weeklyPracticeCount,
                member.getWeeklyGoal(),
                goalProgress,
                todayPracticeCount,
                todayPracticeCount > 0
        );
    }
}
