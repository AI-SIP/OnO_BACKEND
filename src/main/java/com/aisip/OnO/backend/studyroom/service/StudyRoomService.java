package com.aisip.OnO.backend.studyroom.service;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.studyroom.dto.StudyRoomDtos.*;
import com.aisip.OnO.backend.studyroom.dto.StudyRoomStats;
import com.aisip.OnO.backend.studyroom.dto.StudyRoomTodayPracticeSummary;
import com.aisip.OnO.backend.studyroom.entity.StudyRoom;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomMember;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomMemberRole;
import com.aisip.OnO.backend.studyroom.exception.StudyRoomErrorCase;
import com.aisip.OnO.backend.studyroom.repository.StudyRoomMemberRepository;
import com.aisip.OnO.backend.studyroom.repository.StudyRoomRepository;
import com.aisip.OnO.backend.user.entity.User;
import com.aisip.OnO.backend.user.exception.UserErrorCase;
import com.aisip.OnO.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StudyRoomService {

    public static final int MAX_ROOM_MEMBER_COUNT = 20;
    public static final int MAX_USER_ROOM_COUNT = 10;

    private final StudyRoomRepository roomRepository;
    private final StudyRoomMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final StudyRoomAccessService accessService;
    private final StudyRoomStatsService statsService;
    private final StudyRoomMapper mapper;

    @Transactional(readOnly = true)
    public List<StudyRoomListResponse> getMyRooms(Long userId) {
        List<StudyRoomMember> memberships = memberRepository.findAllWithRoomByUserId(userId);
        List<Long> roomIds = memberships.stream().map(member -> member.getRoom().getId()).toList();
        if (roomIds.isEmpty()) {
            return List.of();
        }
        Map<Long, Integer> memberCounts = memberRepository.countMembersByRoomIds(roomIds).stream()
                .collect(java.util.stream.Collectors.toMap(row -> (Long) row[0], row -> Math.toIntExact((Long) row[1])));
        Map<Long, StudyRoomTodayPracticeSummary> todayPracticeSummaries = statsService.getTodayPracticeSummariesByRoomIds(roomIds);
        return memberships.stream()
                .map(member -> mapper.toListResponse(
                        member,
                        memberCounts.getOrDefault(member.getRoom().getId(), 0),
                        todayPracticeSummaries.getOrDefault(member.getRoom().getId(), StudyRoomTodayPracticeSummary.empty())
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public StudyRoomDetailResponse getRoom(Long roomId, Long userId) {
        accessService.validateMember(roomId, userId);
        StudyRoom room = accessService.getRoomOrThrow(roomId);
        return buildDetail(room);
    }

    @Transactional
    public StudyRoomDetailResponse createRoom(StudyRoomCreateRequest request, Long userId) {
        String name = validateName(request.name());
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ApplicationException(UserErrorCase.USER_NOT_FOUND));
        if (memberRepository.countByUserId(userId) >= MAX_USER_ROOM_COUNT) {
            throw new ApplicationException(StudyRoomErrorCase.STUDY_ROOM_LIMIT_EXCEEDED);
        }
        StudyRoom room = StudyRoom.create(name, userId);
        room.addMember(StudyRoomMember.create(user, StudyRoomMemberRole.HOST));
        roomRepository.save(room);
        return buildDetail(room);
    }

    @Transactional
    public void deleteRoom(Long roomId, Long userId) {
        accessService.validateHost(roomId, userId);
        StudyRoom room = lockRoom(roomId);
        roomRepository.delete(room);
    }

    @Transactional
    public StudyRoom lockRoom(Long roomId) {
        return roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new ApplicationException(StudyRoomErrorCase.STUDY_ROOM_NOT_FOUND));
    }

    @Transactional
    public void leaveRoom(Long roomId, Long userId) {
        StudyRoomMember member = accessService.getMemberOrThrow(roomId, userId);
        if (member.getRole() != StudyRoomMemberRole.HOST) {
            memberRepository.deleteByRoomIdAndUserId(roomId, userId);
            return;
        }

        StudyRoom room = roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new ApplicationException(StudyRoomErrorCase.STUDY_ROOM_NOT_FOUND));
        List<StudyRoomMember> members = memberRepository.findAllWithUserByRoomId(roomId);
        StudyRoomMember nextHost = members.stream()
                .filter(candidate -> !candidate.getUser().getId().equals(userId))
                .findFirst()
                .orElse(null);
        if (nextHost == null) {
            roomRepository.delete(room);
            return;
        }
        nextHost.promoteToHost();
        room.updateHostUserId(nextHost.getUser().getId());
        memberRepository.deleteByRoomIdAndUserId(roomId, userId);
    }

    @Transactional
    public void kickMember(Long roomId, Long memberUserId, Long hostUserId) {
        accessService.validateHost(roomId, hostUserId);
        StudyRoomMember member = accessService.getMemberOrThrow(roomId, memberUserId);
        if (member.getRole() == StudyRoomMemberRole.HOST) {
            throw new ApplicationException(StudyRoomErrorCase.STUDY_ROOM_HOST_ONLY);
        }
        memberRepository.deleteByRoomIdAndUserId(roomId, memberUserId);
    }

    @Transactional
    public GoalUpdateResponse updateGoal(Long roomId, Long userId, StudyRoomGoalUpdateRequest request) {
        StudyRoomMember member = accessService.getMemberOrThrow(roomId, userId);
        Integer weeklyGoal = request.weeklyGoal();
        if (weeklyGoal != null && weeklyGoal < 0) {
            throw new ApplicationException(StudyRoomErrorCase.INVALID_STUDY_ROOM_REQUEST);
        }
        member.updateWeeklyGoal(weeklyGoal == null || weeklyGoal == 0 ? null : weeklyGoal);
        StudyRoomStats stats = statsService.getWeeklyStats(List.of(userId)).get(userId);
        return new GoalUpdateResponse(member.getWeeklyGoal(), member.getWeeklyGoal() == null ? null : stats.weeklyProblemCount());
    }

    StudyRoomDetailResponse buildDetail(StudyRoom room) {
        List<StudyRoomMember> members = memberRepository.findAllWithUserByRoomId(room.getId());
        List<Long> userIds = members.stream().map(member -> member.getUser().getId()).toList();
        Map<Long, StudyRoomStats> stats = statsService.getWeeklyStats(userIds);
        Map<Long, Integer> todayPracticeCounts = statsService.getTodayPracticeCounts(userIds);
        return mapper.toDetailResponse(room, members, stats, todayPracticeCounts);
    }

    private String validateName(String name) {
        if (name == null || name.isBlank() || name.length() > 20) {
            throw new ApplicationException(StudyRoomErrorCase.INVALID_STUDY_ROOM_REQUEST);
        }
        return name.trim();
    }
}
