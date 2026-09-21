package com.aisip.OnO.backend.config.rabbitmq;

import org.springframework.retry.RetryContext;
import org.springframework.retry.support.RetrySynchronizationManager;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * RabbitMQ 컨슈머의 처리 시도 횟수를 구한다.
 *
 * <p>재시도는 브로커 재전달이 아니라 리스너 컨테이너의 재시도 인터셉터({@link RabbitMQConfig} 의 advice chain)가
 * <b>같은 스레드 안에서</b> 리스너를 다시 호출하는 방식이다. 그래서 메시지 본문은 첫 시도와 똑같고,
 * 본문의 retryCount 를 올려도 다음 시도에 전달되지 않는다. 시도 횟수는 대신 아래에서 읽는다.
 * <ul>
 *   <li>처리 중: 재시도 인터셉터가 스레드에 걸어 두는 {@link RetryContext} 의 실패 횟수</li>
 *   <li>DLQ: 재시도를 소진한 메시지는 거절(reject)되어 브로커가 dead-letter 하면서 붙이는 {@code x-death} 헤더</li>
 * </ul>
 */
public final class RabbitRetryAttempts {

    /** 첫 시도를 포함한 최대 시도 횟수. 리스너 컨테이너 재시도 정책과 DLQ 알림이 같은 값을 본다. */
    public static final int MAX_ATTEMPTS = 3;

    public static final String X_DEATH_HEADER = "x-death";

    private static final String REJECTED = "rejected";
    private static final String EXPIRED = "expired";

    private RabbitRetryAttempts() {
    }

    /**
     * 지금 처리 중인 시도가 몇 번째인지 돌려준다(1부터).
     * 재시도 인터셉터 밖에서 호출되면(테스트의 직접 호출 등) 컨텍스트가 없으므로 1 로 본다.
     */
    public static int currentAttempt() {
        RetryContext context = RetrySynchronizationManager.getContext();
        return context == null ? 1 : context.getRetryCount() + 1;
    }

    /**
     * 재시도를 모두 소진한 시점(recoverer)에서 실패한 시도 횟수를 설명한다.
     * recoverer 는 재시도 컨텍스트가 닫히기 전에 불리므로 실패 횟수가 그대로 남아 있다.
     */
    static String exhaustedAttempts() {
        RetryContext context = RetrySynchronizationManager.getContext();
        return context == null ? "unknown" : context.getRetryCount() + "/" + MAX_ATTEMPTS;
    }

    /**
     * DLQ 로 들어온 메시지가 원래 큐에서 몇 번 시도됐는지 {@code x-death} 헤더로 설명한다.
     *
     * <p>{@code rejected} 는 재시도 인터셉터가 {@link #MAX_ATTEMPTS} 번을 모두 실패한 뒤 거절한 경우다.
     * 같은 큐에서 여러 번 dead-letter 됐다면(DLQ 메시지를 원래 큐로 되돌려 다시 실패한 경우) count 만큼 곱한다.
     * {@code expired} 는 큐 TTL 이 지나 처리 전에 빠진 경우라 시도 횟수를 알 수 없다.
     *
     * @param xDeath        {@code x-death} 헤더 값. 헤더가 없으면 null
     * @param originalQueue 메시지가 원래 들어 있던 큐 이름
     */
    public static String describeDeadLetter(List<Map<String, ?>> xDeath, String originalQueue) {
        if (xDeath == null || xDeath.isEmpty()) {
            return "알 수 없음 (x-death 헤더 없음)";
        }

        long rejectedCount = 0;
        String latestReason = null;
        for (Map<String, ?> death : xDeath) {
            if (death == null || !Objects.equals(originalQueue, String.valueOf(death.get("queue")))) {
                continue;
            }
            String reason = String.valueOf(death.get("reason"));
            // x-death 는 최근 dead-letter 가 앞에 온다.
            if (latestReason == null) {
                latestReason = reason;
            }
            if (REJECTED.equals(reason) && death.get("count") instanceof Number count) {
                rejectedCount = count.longValue();
            }
        }

        if (latestReason == null) {
            return "알 수 없음 (" + originalQueue + " 의 x-death 기록 없음)";
        }
        if (rejectedCount > 0) {
            String repeated = rejectedCount > 1 ? ", DLQ 이동 " + rejectedCount + "번" : "";
            return (rejectedCount * MAX_ATTEMPTS) + "회 (재시도 소진" + repeated + ")";
        }
        if (EXPIRED.equals(latestReason)) {
            return "알 수 없음 (큐 TTL 만료로 이동)";
        }
        return "알 수 없음 (사유: " + latestReason + ")";
    }
}
