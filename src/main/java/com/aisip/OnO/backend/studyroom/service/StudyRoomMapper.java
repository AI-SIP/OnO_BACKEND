package com.aisip.OnO.backend.studyroom.service;

import com.aisip.OnO.backend.mission.entity.UserMissionStatus;
import com.aisip.OnO.backend.studyroom.dto.StudyRoomDtos.*;
import com.aisip.OnO.backend.studyroom.dto.StudyRoomStats;
import com.aisip.OnO.backend.studyroom.entity.StudyRoom;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomMember;
import com.aisip.OnO.backend.studyroom.repository.StudyRoomWeeklyReportReadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class StudyRoomMapper {

    private final StudyRoomWeeklyReportReadRepository reportReadRepository;

    public StudyRoomListResponse toListResponse(StudyRoomMember member, List<StudyRoomMember> roomMembers,
                                                Map<Long, Integer> todayPracticeCountByUserId) {
        StudyRoom room = member.getRoom();
        int todayPracticeMemberCount = 0;
        int todayPracticeCount = 0;
        List<StudyRoomMemberSummary> memberSummaries = new ArrayList<>(roomMembers.size());
        for (StudyRoomMember m : roomMembers) {
            int count = todayPracticeCountByUserId.getOrDefault(m.getUser().getId(), 0);
            boolean practicedToday = count > 0;
            if (practicedToday) todayPracticeMemberCount++;
            todayPracticeCount += count;
            memberSummaries.add(new StudyRoomMemberSummary(
                    m.getUser().getId(),
                    m.getUser().getProfileImageUrl(),
                    practicedToday
            ));
        }
        return new StudyRoomListResponse(
                room.getId(),
                room.getName(),
                room.getHostUserId(),
                roomMembers.size(),
                room.getThumbnailUrl(),
                reportReadRepository.existsUnreadReport(room.getId(), member.getUser().getId()),
                todayPracticeMemberCount,
                todayPracticeCount,
                memberSummaries
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
                member.getUser().getProfileImageUrl(),
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
