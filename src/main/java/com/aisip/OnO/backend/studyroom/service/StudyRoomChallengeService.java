package com.aisip.OnO.backend.studyroom.service;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.studyroom.dto.StudyRoomDtos.*;
import com.aisip.OnO.backend.studyroom.dto.StudyRoomStats;
import com.aisip.OnO.backend.studyroom.entity.*;
import com.aisip.OnO.backend.studyroom.exception.StudyRoomErrorCase;
import com.aisip.OnO.backend.studyroom.quartz.ChallengeNotificationScheduler;
import com.aisip.OnO.backend.studyroom.repository.StudyRoomChallengeRepository;
import com.aisip.OnO.backend.studyroom.repository.StudyRoomMemberRepository;
import com.aisip.OnO.backend.util.fcm.dto.NotificationRequestDto;
import com.aisip.OnO.backend.util.fcm.service.FcmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.transaction.annotation.Propagation;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudyRoomChallengeService {

    private static final int MAX_IN_PROGRESS_CHALLENGE_COUNT = 5;

    private final StudyRoomAccessService accessService;
    private final StudyRoomChallengeRepository challengeRepository;
    private final StudyRoomMemberRepository memberRepository;
    private final StudyRoomStatsService statsService;
    private final ChallengeNotificationScheduler notificationScheduler;
    private final FcmService fcmService;

    @Transactional
    public List<ChallengeResponse> getChallenges(Long roomId, Long userId) {
        accessService.validateMember(roomId, userId);
        List<StudyRoomMember> members = memberRepository.findAllWithUserByRoomId(roomId);
        return challengeRepository.findAllByRoomIdOrderByEndAtAsc(roomId).stream()
                .map(challenge -> toResponse(challenge, members, true))
                .sorted(Comparator.comparing((ChallengeResponse r) -> "in_progress".equals(r.status()) ? 0 : 1)
                        .thenComparing(Comparator.comparing(ChallengeResponse::endAt).reversed()))
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
                period == null ? request.periodDays() : null,
                request.targetValue(),
                startAt,
                request.endAt()
        ));
        notificationScheduler.scheduleNotifications(challenge);
        return toResponse(challenge, memberRepository.findAllWithUserByRoomId(roomId), true);
    }

    @Transactional
    public void deleteChallenge(Long roomId, Long challengeId, Long userId) {
        accessService.validateHost(roomId, userId);
        StudyRoomChallenge challenge = challengeRepository.findByIdAndRoomId(challengeId, roomId)
                .orElseThrow(() -> new ApplicationException(StudyRoomErrorCase.CHALLENGE_NOT_FOUND));
        notificationScheduler.cancelNotifications(challengeId);
        challengeRepository.delete(challenge);
    }

    private void refreshRoomChallengeStatuses(Long roomId) {
        List<StudyRoomMember> members = memberRepository.findAllWithUserByRoomId(roomId);
        challengeRepository.findAllByRoomIdOrderByEndAtAsc(roomId)
                .forEach(challenge -> toResponse(challenge, members, true));
    }

    private ChallengeResponse toResponse(StudyRoomChallenge challenge, List<StudyRoomMember> members, boolean persistStatus) {
        List<Long> userIds = members.stream().map(member -> member.getUser().getId()).toList();
        AggregationRange range = resolveAggregationRange(challenge, LocalDateTime.now());
        LocalDate streakBaseDate = min(LocalDate.now(), challenge.getEndAt().toLocalDate());
        Map<Long, Integer> streaks = statsService.currentStreaks(userIds, streakBaseDate, challenge.getStartAt().toLocalDate());
        Map<Long, StudyRoomStats> stats = statsService.getStats(userIds, range.start(), range.end(), streaks);
        Map<Long, Integer> attendanceDays = isAttendanceMetric(challenge.getMetric())
                ? statsService.attendanceDayCounts(userIds, range.start(), range.end())
                : Map.of();
        List<ChallengeMemberProgressResponse> memberProgress = challenge.getType() == StudyRoomChallengeType.GROUP
                ? List.of()
                : members.stream()
                .map(member -> {
                    int current = metricValue(challenge.getMetric(), stats.get(member.getUser().getId()),
                            attendanceDays.getOrDefault(member.getUser().getId(), 0));
                    return new ChallengeMemberProgressResponse(member.getUser().getId(), member.getUser().getName(),
                            member.getUser().getProfileImageUrl(), current, current >= challenge.getTargetValue());
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
            if (status == StudyRoomChallengeStatus.COMPLETED) {
                // 원자적 UPDATE WHERE status = 'IN_PROGRESS' — 단 하나의 스레드만 1을 반환해 FCM 중복 발송 방지
                int updated = challengeRepository.tryTransitionFromInProgress(challenge.getId(), status, LocalDateTime.now());
                if (updated > 0) {
                    challenge.updateStatus(status);
                    notifyChallengeCompleted(challenge, members);
                }
            } else {
                challenge.updateStatus(status);
            }
        }
        return new ChallengeResponse(
                challenge.getId(),
                challenge.getTitle(),
                toApiValue(challenge.getType().name()),
                toApiValue(challenge.getMetric().name()),
                challenge.getPeriod() == null ? null : toApiValue(challenge.getPeriod().name()),
                challenge.getPeriodDays(),
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
        if (challenge.getPeriod() == null && challenge.getPeriodDays() == null) {
            boolean completed = challenge.getType() == StudyRoomChallengeType.GROUP
                    ? groupCurrent != null && groupCurrent >= challenge.getTargetValue()
                    : !memberProgress.isEmpty() && memberProgress.stream().allMatch(ChallengeMemberProgressResponse::cleared);
            if (completed) {
                return StudyRoomChallengeStatus.COMPLETED;
            }
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

    private AggregationRange resolveAggregationRange(StudyRoomChallenge challenge, LocalDateTime now) {
        LocalDateTime startAt = challenge.getStartAt();
        LocalDateTime endAt = challenge.getEndAt();
        StudyRoomChallengePeriod period = challenge.getPeriod();
        Integer periodDays = challenge.getPeriodDays();
        if (period == null && periodDays == null) {
            return new AggregationRange(startAt, endAt);
        }
        LocalDateTime anchor = clamp(now, startAt, endAt);
        LocalDateTime windowStart = startAt;
        LocalDateTime windowEnd = addPeriod(windowStart, period, periodDays);
        while (!windowEnd.isAfter(anchor) && windowEnd.isBefore(endAt)) {
            windowStart = windowEnd;
            windowEnd = addPeriod(windowStart, period, periodDays);
        }
        return new AggregationRange(windowStart, windowEnd.isAfter(endAt) ? endAt : windowEnd);
    }

    private LocalDateTime addPeriod(LocalDateTime base, StudyRoomChallengePeriod period, Integer periodDays) {
        if (period != null) {
            return switch (period) {
                case DAILY -> base.plusDays(1);
                case WEEKLY -> base.plusDays(7);
                case MONTHLY -> base.plusMonths(1);
            };
        }
        return base.plusDays(periodDays);
    }

    private LocalDateTime clamp(LocalDateTime value, LocalDateTime lower, LocalDateTime upper) {
        if (value.isBefore(lower)) {
            return lower;
        }
        if (value.isAfter(upper)) {
            return upper;
        }
        return value;
    }

    private record AggregationRange(LocalDateTime start, LocalDateTime end) {}

    private void validateCreateRequest(ChallengeCreateRequest request) {
        if (request.title() == null || request.title().isBlank() || request.title().length() > 40
                || request.targetValue() == null || request.targetValue() < 1
                || request.endAt() == null || !request.endAt().isAfter(LocalDateTime.now())
                || request.startAt() != null && !request.startAt().isBefore(request.endAt())
                || request.period() != null && request.periodDays() != null
                || request.periodDays() != null && request.periodDays() < 1) {
            throw new ApplicationException(StudyRoomErrorCase.INVALID_STUDY_ROOM_REQUEST);
        }
    }

    /**
     * 사용자의 학습 활동 이후 해당 사용자가 속한 룸들의 IN_PROGRESS 챌린지 완료 여부를 즉시 확인합니다.
     * REQUIRES_NEW로 독립 트랜잭션 실행 — 실패해도 피드 생성 트랜잭션에 영향 없음.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void checkAndNotifyChallengeCompletionForUser(Long userId) {
        List<StudyRoomMember> memberships = memberRepository.findAllWithRoomByUserId(userId);
        if (memberships.isEmpty()) {
            return;
        }

        List<Long> roomIds = memberships.stream()
                .map(m -> m.getRoom().getId())
                .toList();
        List<StudyRoomChallenge> inProgressChallenges =
                challengeRepository.findAllByRoomIdsAndStatus(roomIds, StudyRoomChallengeStatus.IN_PROGRESS);
        if (inProgressChallenges.isEmpty()) {
            return;
        }

        Map<Long, List<StudyRoomMember>> membersByRoom = new HashMap<>();
        for (StudyRoomChallenge challenge : inProgressChallenges) {
            Long roomId = challenge.getRoom().getId();
            membersByRoom.computeIfAbsent(roomId, id -> memberRepository.findAllWithUserByRoomId(id));
            toResponse(challenge, membersByRoom.get(roomId), true);
        }
    }

    private void notifyChallengeCompleted(StudyRoomChallenge challenge, List<StudyRoomMember> members) {
        NotificationRequestDto dto = new NotificationRequestDto(null,
                "챌린지 달성! 🎉",
                "'" + challenge.getTitle() + "' 챌린지를 달성했어요!",
                Map.of("type", "CHALLENGE_COMPLETED", "roomId", String.valueOf(challenge.getRoom().getId())));
        members.forEach(member -> {
            try {
                fcmService.sendNotificationToAllUserDevice(member.getUser().getId(), dto);
            } catch (Exception e) {
                log.warn("챌린지 달성 알림 발송 실패 - userId: {}", member.getUser().getId(), e);
            }
        });
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
