package com.aisip.OnO.backend.studyroom.quartz;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

/**
 * 챌린지 알림을 언제 보낼지 정한다.
 *
 * <p>계산된 시각을 그대로 쓰면 한밤중에 푸시가 나간다. 앱이 마감을 그날 23:59:59 로 보내서
 * D-1 알림은 거의 항상 밤 23:59 였고, 중간 알림은 챌린지를 만든 시각을 그대로 따라가 새벽에 걸렸다.
 * 그래서 발송 시각은 아침 09:00 과 저녁 18:00 두 기준 시각으로만 맞춘다.
 *
 * <p>복습 리마인더의 {@code ProblemReviewReminderPolicy} 는 09~21시 "구간" 안이면 그대로 두지만,
 * 챌린지 알림은 방 멤버 전원에게 한 번에 나가는 알림이라 시각을 두 지점으로 못박는 편이 예측하기 쉽다.
 */
@Component
public class ChallengeNotificationTimePolicy {

    private static final LocalTime MORNING = LocalTime.of(9, 0);
    private static final LocalTime EVENING = LocalTime.of(18, 0);

    /**
     * 중간 알림 발송 시각. 기준은 기존과 같이 시작과 마감의 중간 지점이다.
     */
    public Optional<LocalDateTime> resolveHalfwayFireAt(LocalDateTime startAt, LocalDateTime endAt, LocalDateTime now) {
        LocalDateTime halfwayAt = startAt.plus(Duration.between(startAt, endAt).dividedBy(2));
        return resolveFireAt(halfwayAt, now, endAt);
    }

    /**
     * D-1 알림 발송 시각. 기준은 기존과 같이 마감 하루 전이다.
     *
     * <p>기간이 하루 이하인 챌린지는 마감 하루 전이 시작 시각보다도 이르다. 그 시점을 억지로 미루면
     * 중간 알림과 같은 시각에 두 번 나가므로 아예 보내지 않는다.
     */
    public Optional<LocalDateTime> resolveOneDayLeftFireAt(LocalDateTime startAt, LocalDateTime endAt, LocalDateTime now) {
        LocalDateTime oneDayBeforeAt = endAt.minusDays(1);
        if (oneDayBeforeAt.isBefore(startAt)) {
            return Optional.empty();
        }
        return resolveFireAt(oneDayBeforeAt, now, endAt);
    }

    /**
     * 계산된 시각을 기준 시각(09:00 / 18:00)으로 맞춘다.
     *
     * <p>같은 날 두 기준 시각 중 가까운 쪽으로 옮기고(23:59 면 그날 18:00, 새벽 3시면 그날 09:00),
     * 그 시각이 이미 지났으면 다음 기준 시각으로 넘긴다. 마감을 넘기면 보내지 않는다.
     * 마감이 지난 뒤에 오는 "내일 마감" 알림은 의미가 없기 때문이다.
     *
     * @return 보낼 시각, 보낼 수 없으면 {@link Optional#empty()}
     */
    private Optional<LocalDateTime> resolveFireAt(LocalDateTime candidate, LocalDateTime now, LocalDateTime deadline) {
        LocalDateTime fireAt = snapToNearestAnchor(candidate);
        while (!fireAt.isAfter(now) && !fireAt.isAfter(deadline)) {
            fireAt = nextAnchor(fireAt);
        }
        if (!fireAt.isAfter(now) || fireAt.isAfter(deadline)) {
            return Optional.empty();
        }
        return Optional.of(fireAt);
    }

    /**
     * 같은 날 09:00 과 18:00 중 가까운 쪽. 정확히 가운데(13:30)면 이른 쪽으로 보낸다.
     */
    private LocalDateTime snapToNearestAnchor(LocalDateTime candidate) {
        LocalDateTime morning = candidate.toLocalDate().atTime(MORNING);
        LocalDateTime evening = candidate.toLocalDate().atTime(EVENING);
        long toMorning = Math.abs(Duration.between(morning, candidate).toSeconds());
        long toEvening = Math.abs(Duration.between(evening, candidate).toSeconds());
        return toEvening < toMorning ? evening : morning;
    }

    private LocalDateTime nextAnchor(LocalDateTime anchor) {
        LocalTime time = anchor.toLocalTime();
        if (time.isBefore(MORNING)) {
            return anchor.toLocalDate().atTime(MORNING);
        }
        if (time.isBefore(EVENING)) {
            return anchor.toLocalDate().atTime(EVENING);
        }
        return anchor.toLocalDate().plusDays(1).atTime(MORNING);
    }
}
