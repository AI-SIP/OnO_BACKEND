package com.aisip.OnO.blackbox.support;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 블랙박스 시나리오의 공통 베이스.
 *
 * <p>앱은 서버 오류(5xx)가 나면 Discord 웹훅으로 알린다(GlobalExceptionHandler). 블랙박스에서는 그 웹훅이
 * WireMock 이라, 시나리오 하나가 끝났을 때 알림이 늘었으면 그 사이 어딘가에서 500 이 났다는 뜻이다.
 * 응답 코드를 일일이 보지 않은 요청이나 비동기 처리에서 난 서버 오류도 여기서 걸린다.
 * 일부러 서버 오류를 내는 시나리오(외부 연동 장애 등)는 {@link #allowServerErrorAlerts()} 를 부른다.
 */
public abstract class BlackboxTest {

    private static final String DISCORD_PATH = "/discord-webhook";
    /** 가입 알림 같은 다른 Discord 메시지와 가르기 위해 서버 오류 알림 제목으로 센다. */
    private static final String SERVER_ERROR_TITLE = "서버 에러 발생";

    private int discordAlertsBefore;
    private boolean serverErrorAlertsAllowed;

    @BeforeEach
    void recordDiscordAlerts() {
        BlackboxStack.get();
        serverErrorAlertsAllowed = false;
        discordAlertsBefore = serverErrorAlerts();
    }

    @AfterEach
    void noUnexpectedServerError() {
        if (serverErrorAlertsAllowed) {
            return;
        }
        // 알림은 RabbitMQ 를 거쳐 비동기로 나가므로 잠깐 기다린다.
        sleep(Duration.ofMillis(700));
        int after = serverErrorAlerts();
        assertThat(after - discordAlertsBefore)
                .as("시나리오 중 서버 오류 알림이 나갔습니다. build/blackbox/app.log 를 확인하세요. 알림: %s",
                        WireMock.requests("POST", DISCORD_PATH))
                .isZero();
    }

    private static int serverErrorAlerts() {
        return WireMock.countContaining("POST", DISCORD_PATH, SERVER_ERROR_TITLE);
    }

    protected static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    protected void allowServerErrorAlerts() {
        serverErrorAlertsAllowed = true;
    }

    /**
     * 비동기 처리(분석, 피드, S3 삭제)가 끝나기를 기다린다. 고정 sleep 대신 조건이 맞을 때까지 짧게 다시 본다.
     */
    protected static <T> T eventually(Duration timeout, Supplier<T> attempt) {
        Instant deadline = Instant.now().plus(timeout);
        AssertionError last = null;
        while (Instant.now().isBefore(deadline)) {
            try {
                return attempt.get();
            } catch (AssertionError e) {
                last = e;
            }
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        if (last != null) {
            throw last;
        }
        throw new AssertionError("시간 안에 조건이 맞지 않았습니다");
    }

    protected static void eventually(Duration timeout, Runnable attempt) {
        eventually(timeout, () -> {
            attempt.run();
            return null;
        });
    }
}
