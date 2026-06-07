package com.aisip.OnO.backend.studyroom.service;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.studyroom.dto.StudyRoomDtos.*;
import com.aisip.OnO.backend.studyroom.dto.StudyRoomStats;
import com.aisip.OnO.backend.studyroom.entity.*;
import com.aisip.OnO.backend.studyroom.exception.StudyRoomErrorCase;
import com.aisip.OnO.backend.studyroom.repository.StudyRoomChallengeRepository;
import com.aisip.OnO.backend.studyroom.repository.StudyRoomMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StudyRoomChallengeService {

    private static final int MAX_IN_PROGRESS_CHALLENGE_COUNT = 5;

    private final StudyRoomAccessService accessService;
    private final StudyRoomChallengeRepository challengeRepository;
    private final StudyRoomMemberRepository memberRepository;
    private final StudyRoomStatsService statsService;

    @Transactional(readOnly = true)
    public List<ChallengeResponse> getChallenges(Long roomId, Long userId) {
        accessService.validateMember(roomId, userId);
        List<StudyRoomMember> members = memberRepository.findAllWithUserByRoomId(roomId);
        return challengeRepository.findActiveByRoomId(roomId, StudyRoomChallengeStatus.IN_PROGRESS, LocalDateTime.now()).stream()
                .map(challenge -> toResponse(challenge, members, false))
                .toList();
    }

    @Transactional
    public ChallengeResponse createChallenge(Long roomId, Long userId, ChallengeCreateRequest request) {
        accessService.validateMember(roomId, userId);
        validateCreateRequest(request);
        refreshRoomChallengeStatuses(roomId);
        if (challengeRepository.countByRoomIdAndStatus(roomId, StudyRoomChallengeStatus.IN_PROGRESS) >= MAX_IN_PROGRESS_CHALLENGE_COUNT) {
            throw new ApplicationException(StudyRoomErrorCase.CHALLENGE_LIMIT_EXCEEDED);
        }
        LocalDateTime startAt = request.startAt() == null ? LocalDateTime.now() : request.startAt();
        StudyRoomChallengePeriod period = request.period() == null
                ? null
                : parseEnum(request.period(), StudyRoomChallengePeriod.class);
        StudyRoom room = accessService.getRoomOrThrow(roomId);
        StudyRoomChallenge challenge = challengeRepository.save(StudyRoomChallenge.create(
                room,
                request.title().trim(),
                parseEnum(request.type(), StudyRoomChallengeType.class),
                parseEnum(request.metric(), StudyRoomChallengeMetric.class),
                period,
                request.targetValue(),
                startAt,
                request.endAt()
        ));
        return toResponse(challenge, memberRepository.findAllWithUserByRoomId(roomId), true);
    }

    @Transactional
    public void deleteChallenge(Long roomId, Long challengeId, Long userId) {
        accessService.validateHost(roomId, userId);
        StudyRoomChallenge challenge = challengeRepository.findByIdAndRoomId(challengeId, roomId)
                .orElseThrow(() -> new ApplicationException(StudyRoomErrorCase.CHALLENGE_NOT_FOUND));
        challengeRepository.delete(challenge);
    }

    private void refreshRoomChallengeStatuses(Long roomId) {
        List<StudyRoomMember> members = memberRepository.findAllWithUserByRoomId(roomId);
        challengeRepository.findAllByRoomIdOrderByEndAtAsc(roomId)
                .forEach(challenge -> toResponse(challenge, members, true));
    }

    private ChallengeResponse toResponse(StudyRoomChallenge challenge, List<StudyRoomMember> members, boolean persistStatus) {
        List<Long> userIds = members.stream().map(member -> member.getUser().getId()).toList();
        LocalDate streakBaseDate = min(LocalDate.now(), challenge.getEndAt().toLocalDate());
        Map<Long, Integer> streaks = statsService.currentStreaks(userIds, streakBaseDate, challenge.getStartAt().toLocalDate());
        Map<Long, StudyRoomStats> stats = statsService.getStats(userIds, challenge.getStartAt(), challenge.getEndAt(), streaks);
        Map<Long, Integer> attendanceDays = isAttendanceMetric(challenge.getMetric())
                ? statsService.attendanceDayCounts(userIds, challenge.getStartAt(), challenge.getEndAt())
                : Map.of();
        List<ChallengeMemberProgressResponse> memberProgress = challenge.getType() == StudyRoomChallengeType.GROUP
                ? List.of()
                : members.stream()
                .map(member -> {
                    int current = metricValue(challenge.getMetric(), stats.get(member.getUser().getId()),
                            attendanceDays.getOrDefault(member.getUser().getId(), 0));
                    return new ChallengeMemberProgressResponse(member.getUser().getId(), member.getUser().getName(),
                            current, current >= challenge.getTargetValue());
                })
                .toList();
        Integer groupCurrent = challenge.getType() == StudyRoomChallengeType.GROUP
                ? userIds.stream()
                .mapToInt(memberId -> metricValue(challenge.getMetric(), stats.get(memberId),
                        attendanceDays.getOrDefault(memberId, 0)))
                .sum()
                : null;
        StudyRoomChallengeStatus status = effectiveStatus(challenge, memberProgress, groupCurrent);
        if (persistStatus && challenge.getStatus() != status) {
            challenge.updateStatus(status);
        }
        return new ChallengeResponse(
                challenge.getId(),
                challenge.getTitle(),
                toApiValue(challenge.getType().name()),
                toApiValue(challenge.getMetric().name()),
                challenge.getPeriod() == null ? null : toApiValue(challenge.getPeriod().name()),
                challenge.getTargetValue(),
                challenge.getStartAt(),
                challenge.getEndAt(),
                toApiValue(status.name()),
                memberProgress,
                groupCurrent
        );
    }

    private StudyRoomChallengeStatus effectiveStatus(StudyRoomChallenge challenge,
                                                    List<ChallengeMemberProgressResponse> memberProgress,
                                                    Integer groupCurrent) {
        if (challenge.getStatus() != StudyRoomChallengeStatus.IN_PROGRESS) {
            return challenge.getStatus();
        }
        boolean completed = challenge.getType() == StudyRoomChallengeType.GROUP
                ? groupCurrent != null && groupCurrent >= challenge.getTargetValue()
                : !memberProgress.isEmpty() && memberProgress.stream().allMatch(ChallengeMemberProgressResponse::cleared);
        if (completed) {
            return StudyRoomChallengeStatus.COMPLETED;
        }
        if (challenge.getEndAt().isBefore(LocalDateTime.now())) {
            return StudyRoomChallengeStatus.EXPIRED;
        }
        return StudyRoomChallengeStatus.IN_PROGRESS;
    }

    private int metricValue(StudyRoomChallengeMetric metric, StudyRoomStats stats, int attendanceDays) {
        if (stats == null) {
            return 0;
        }
        return switch (metric) {
            case PROBLEM_COUNT, WEEKLY_PROBLEM_COUNT -> stats.weeklyProblemCount();
            case PRACTICE_COUNT, WEEKLY_PRACTICE_COUNT -> stats.weeklyPracticeCount();
            case ATTENDANCE, STREAK -> attendanceDays;
        };
    }

    private boolean isAttendanceMetric(StudyRoomChallengeMetric metric) {
        return metric == StudyRoomChallengeMetric.ATTENDANCE || metric == StudyRoomChallengeMetric.STREAK;
    }

    private void validateCreateRequest(ChallengeCreateRequest request) {
        if (request.title() == null || request.title().isBlank() || request.title().length() > 40
                || request.targetValue() == null || request.targetValue() < 1
                || request.endAt() == null || !request.endAt().isAfter(LocalDateTime.now())
                || request.startAt() != null && !request.startAt().isBefore(request.endAt())) {
            throw new ApplicationException(StudyRoomErrorCase.INVALID_STUDY_ROOM_REQUEST);
        }
    }

    private LocalDate min(LocalDate left, LocalDate right) {
        return left.isBefore(right) ? left : right;
    }

    private <T extends Enum<T>> T parseEnum(String value, Class<T> enumClass) {
        try {
            return Enum.valueOf(enumClass, value.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            throw new ApplicationException(StudyRoomErrorCase.INVALID_STUDY_ROOM_REQUEST);
        }
    }

    private String toApiValue(String enumName) {
        return enumName.toLowerCase(Locale.ROOT);
    }
}
