package com.aisip.OnO.backend.studyroom.service;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.studyroom.dto.StudyRoomDtos.*;
import com.aisip.OnO.backend.studyroom.dto.StudyRoomStats;
import com.aisip.OnO.backend.studyroom.entity.*;
import com.aisip.OnO.backend.studyroom.exception.StudyRoomErrorCase;
import com.aisip.OnO.backend.studyroom.repository.*;
import com.aisip.OnO.backend.user.entity.User;
import com.aisip.OnO.backend.user.exception.UserErrorCase;
import com.aisip.OnO.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StudyRoomWeeklyReportService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String DEFAULT_CHEER_MESSAGE = "이번 주도 모두 고생했어요!";
    private static final int REPORT_ROOM_BATCH_SIZE = 100;

    private final StudyRoomAccessService accessService;
    private final StudyRoomRepository roomRepository;
    private final StudyRoomMemberRepository memberRepository;
    private final StudyRoomWeeklyReportRepository reportRepository;
    private final StudyRoomWeeklyReportReadRepository readRepository;
    private final StudyRoomChallengeRepository challengeRepository;
    private final StudyRoomStatsService statsService;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<WeeklyReportResponse> getReports(Long roomId, Long userId, int limit) {
        accessService.validateMember(roomId, userId);
        int safeLimit = Math.max(1, Math.min(limit, 12));
        List<StudyRoomWeeklyReport> reports = reportRepository.findAllByRoomIdOrderByWeekStartDesc(roomId, PageRequest.of(0, safeLimit));
        Set<Long> readReportIds = readRepository.findAllByReportIdsAndUserId(
                        reports.stream().map(StudyRoomWeeklyReport::getId).toList(), userId)
                .stream()
                .map(read -> read.getReport().getId())
                .collect(Collectors.toSet());
        return reports.stream().map(report -> toResponse(report, readReportIds.contains(report.getId()))).toList();
    }

    @Transactional
    public WeeklyReportReadResponse markRead(Long roomId, Long reportId, Long userId) {
        accessService.validateMember(roomId, userId);
        StudyRoomWeeklyReport report = reportRepository.findByIdAndRoomId(reportId, roomId)
                .orElseThrow(() -> new ApplicationException(StudyRoomErrorCase.REPORT_NOT_FOUND));
        if (readRepository.findByReportIdAndUserId(reportId, userId).isEmpty()) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ApplicationException(UserErrorCase.USER_NOT_FOUND));
            readRepository.save(StudyRoomWeeklyReportRead.create(report, user, LocalDateTime.now()));
        }
        return new WeeklyReportReadResponse(reportId, true);
    }

    @Transactional
    public void createPreviousWeekReports() {
        LocalDate today = LocalDate.now(KST);
        LocalDate weekStart = today.with(TemporalAdjusters.previous(DayOfWeek.MONDAY));
        LocalDate weekEnd = weekStart.plusDays(6);
        LocalDateTime start = weekStart.atStartOfDay();
        LocalDateTime end = weekEnd.atTime(LocalTime.MAX);
        Long cursor = 0L;
        while (true) {
            List<StudyRoom> rooms = roomRepository.findBatchAfterId(cursor, PageRequest.of(0, REPORT_ROOM_BATCH_SIZE));
            if (rooms.isEmpty()) {
                return;
            }
            createReportsForBatch(rooms, weekStart, weekEnd, start, end);
            cursor = rooms.get(rooms.size() - 1).getId();
        }
    }

    void createReportIfAbsent(StudyRoom room, LocalDate weekStart, LocalDate weekEnd, LocalDateTime start, LocalDateTime end) {
        List<StudyRoomMember> members = memberRepository.findAllWithUserByRoomId(room.getId());
        List<Long> userIds = members.stream().map(member -> member.getUser().getId()).toList();
        Map<Long, StudyRoomStats> stats = statsService.getStats(userIds, start, end, weekEnd);
        createReportIfAbsent(room, members, stats, weekStart, weekEnd, 0);
    }

    private void createReportsForBatch(List<StudyRoom> rooms, LocalDate weekStart, LocalDate weekEnd,
                                       LocalDateTime start, LocalDateTime end) {
        List<Long> roomIds = rooms.stream().map(StudyRoom::getId).toList();
        List<StudyRoomMember> members = memberRepository.findAllWithRoomAndUserByRoomIds(roomIds);
        Map<Long, List<StudyRoomMember>> membersByRoomId = members.stream()
                .collect(Collectors.groupingBy(member -> member.getRoom().getId(), LinkedHashMap::new, Collectors.toList()));
        List<Long> userIds = members.stream().map(member -> member.getUser().getId()).distinct().toList();
        Map<Long, Integer> streaks = statsService.currentStreaks(userIds, weekEnd);
        Map<Long, StudyRoomStats> statsByUserId = statsService.getStats(userIds, start, end, streaks);
        refreshChallengeStatuses(roomIds, membersByRoomId);
        Map<Long, Integer> completedChallengeCounts = completedChallengeCounts(roomIds, start, end);

        for (StudyRoom room : rooms) {
            List<StudyRoomMember> roomMembers = membersByRoomId.getOrDefault(room.getId(), List.of());
            Map<Long, StudyRoomStats> roomStats = roomMembers.stream()
                    .collect(Collectors.toMap(
                            member -> member.getUser().getId(),
                            member -> statsByUserId.getOrDefault(member.getUser().getId(), new StudyRoomStats(0, 0, 0))
                    ));
            createReportIfAbsent(room, roomMembers, roomStats, weekStart, weekEnd,
                    completedChallengeCounts.getOrDefault(room.getId(), 0));
        }
    }

    private void createReportIfAbsent(StudyRoom room, List<StudyRoomMember> members, Map<Long, StudyRoomStats> stats,
                                      LocalDate weekStart, LocalDate weekEnd, int challengesCompleted) {
        if (reportRepository.existsByRoomIdAndWeekStart(room.getId(), weekStart)) {
            return;
        }
        StudyRoomMember topProblemMember = members.stream()
                .max(Comparator.comparingInt(member -> stats.getOrDefault(member.getUser().getId(), new StudyRoomStats(0, 0, 0)).weeklyProblemCount()))
                .orElse(null);
        StudyRoomMember topStreakMember = members.stream()
                .max(Comparator.comparingInt(member -> stats.getOrDefault(member.getUser().getId(), new StudyRoomStats(0, 0, 0)).currentStreak()))
                .orElse(null);
        int topProblems = topProblemMember == null ? 0 : stats.getOrDefault(topProblemMember.getUser().getId(), new StudyRoomStats(0, 0, 0)).weeklyProblemCount();
        int longestStreak = topStreakMember == null ? 0 : stats.getOrDefault(topStreakMember.getUser().getId(), new StudyRoomStats(0, 0, 0)).currentStreak();
        int totalProblems = stats.values().stream().mapToInt(StudyRoomStats::weeklyProblemCount).sum();
        reportRepository.save(StudyRoomWeeklyReport.create(
                room,
                weekStart,
                weekEnd,
                topProblemMember == null ? null : topProblemMember.getUser().getName(),
                topProblems,
                topStreakMember == null ? null : topStreakMember.getUser().getName(),
                longestStreak,
                totalProblems,
                challengesCompleted,
                DEFAULT_CHEER_MESSAGE
        ));
    }

    private Map<Long, Integer> completedChallengeCounts(List<Long> roomIds, LocalDateTime start, LocalDateTime end) {
        Map<Long, Integer> result = new HashMap<>();
        challengeRepository.countByRoomIdsAndStatusAndEndAtBetween(roomIds, StudyRoomChallengeStatus.COMPLETED, start, end)
                .forEach(row -> result.put((Long) row[0], Math.toIntExact((Long) row[1])));
        return result;
    }

    private void refreshChallengeStatuses(List<Long> roomIds, Map<Long, List<StudyRoomMember>> membersByRoomId) {
        List<StudyRoomChallenge> challenges = challengeRepository.findAllByRoomIdsAndStatus(roomIds, StudyRoomChallengeStatus.IN_PROGRESS);
        for (StudyRoomChallenge challenge : challenges) {
            List<StudyRoomMember> members = membersByRoomId.getOrDefault(challenge.getRoom().getId(), List.of());
            List<Long> userIds = members.stream().map(member -> member.getUser().getId()).toList();
            Map<Long, Integer> streaks = statsService.currentStreaks(
                    userIds,
                    min(LocalDate.now(KST), challenge.getEndAt().toLocalDate()),
                    challenge.getStartAt().toLocalDate()
            );
            Map<Long, StudyRoomStats> stats = statsService.getStats(userIds, challenge.getStartAt(), challenge.getEndAt(), streaks);
            Map<Long, Integer> attendanceDays = isAttendanceMetric(challenge.getMetric())
                    ? statsService.attendanceDayCounts(userIds, challenge.getStartAt(), challenge.getEndAt())
                    : Map.of();
            if (isChallengeCompleted(challenge, members, stats, attendanceDays)) {
                challenge.updateStatus(StudyRoomChallengeStatus.COMPLETED);
                continue;
            }
            if (challenge.getEndAt().isBefore(LocalDateTime.now())) {
                challenge.updateStatus(StudyRoomChallengeStatus.EXPIRED);
            }
        }
    }

    private boolean isChallengeCompleted(StudyRoomChallenge challenge, List<StudyRoomMember> members,
                                         Map<Long, StudyRoomStats> stats, Map<Long, Integer> attendanceDays) {
        if (members.isEmpty()) {
            return false;
        }
        if (challenge.getType() == StudyRoomChallengeType.GROUP) {
            int groupCurrent = members.stream()
                    .mapToInt(member -> metricValue(challenge.getMetric(),
                            stats.getOrDefault(member.getUser().getId(), new StudyRoomStats(0, 0, 0)),
                            attendanceDays.getOrDefault(member.getUser().getId(), 0)))
                    .sum();
            return groupCurrent >= challenge.getTargetValue();
        }
        return members.stream()
                .allMatch(member -> metricValue(challenge.getMetric(),
                        stats.getOrDefault(member.getUser().getId(), new StudyRoomStats(0, 0, 0)),
                        attendanceDays.getOrDefault(member.getUser().getId(), 0)) >= challenge.getTargetValue());
    }

    private int metricValue(StudyRoomChallengeMetric metric, StudyRoomStats stats, int attendanceDays) {
        return switch (metric) {
            case PROBLEM_COUNT, WEEKLY_PROBLEM_COUNT -> stats.weeklyProblemCount();
            case PRACTICE_COUNT, WEEKLY_PRACTICE_COUNT -> stats.weeklyPracticeCount();
            case ATTENDANCE, STREAK -> attendanceDays;
        };
    }

    private boolean isAttendanceMetric(StudyRoomChallengeMetric metric) {
        return metric == StudyRoomChallengeMetric.ATTENDANCE || metric == StudyRoomChallengeMetric.STREAK;
    }

    private LocalDate min(LocalDate left, LocalDate right) {
        return left.isBefore(right) ? left : right;
    }

    private WeeklyReportResponse toResponse(StudyRoomWeeklyReport report, boolean read) {
        return new WeeklyReportResponse(report.getId(), report.getWeekStart(), report.getWeekEnd(),
                report.getTopMemberName(), report.getTopMemberProblemCount(),
                report.getLongestStreakName(), report.getLongestStreakDays(),
                report.getTotalProblems(), report.getChallengesCompleted(),
                report.getCheerMessage(), read);
    }
}
