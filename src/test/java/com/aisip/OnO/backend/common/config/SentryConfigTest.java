package com.aisip.OnO.backend.common.config;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.common.exception.ErrorCase;
import io.sentry.Hint;
import io.sentry.Sentry;
import io.sentry.SentryEvent;
import io.sentry.SentryOptions;
import io.sentry.protocol.Message;
import io.sentry.spring.boot.jakarta.SentryAutoConfiguration;
import io.sentry.spring.boot.jakarta.SentryProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class SentryConfigTest {

    private final SentryConfig sentryConfig = new SentryConfig();

    @Nested
    @DisplayName("beforeSend 필터")
    class BeforeSend {

        private final SentryOptions.BeforeSendCallback callback = sentryConfig.sentryBeforeSendCallback();

        @Test
        @DisplayName("5xx 접근 로그(HTTP request completed)는 원래 예외 이벤트와 중복이라 보내지 않는다")
        void dropsRequestCompletedLog() {
            SentryEvent event = logEvent(RequestLoggingMdcFilter.class.getName(), "HTTP request completed");

            assertThat(callback.execute(event, new Hint())).isNull();
        }

        @Test
        @DisplayName("MCP SDK 의 미지원 메서드 로그(server/discover)는 보내지 않는다")
        void dropsMcpMissingHandlerLog() {
            SentryEvent event = logEvent(SentryConfig.MCP_TRANSPORT_LOGGER,
                    "Failed to handle request: Missing handler for request type: server/discover");

            assertThat(callback.execute(event, new Hint())).isNull();
        }

        @Test
        @DisplayName("MCP 미지원 메서드 문구가 예외 메시지에만 있어도 보내지 않는다")
        void dropsMcpMissingHandlerByThrowable() {
            SentryEvent event = new SentryEvent(
                    new IllegalStateException("Missing handler for request type: server/discover"));
            event.setLogger(SentryConfig.MCP_TRANSPORT_LOGGER);

            assertThat(callback.execute(event, new Hint())).isNull();
        }

        @Test
        @DisplayName("MCP 전송 계층의 다른 오류는 그대로 보낸다")
        void keepsOtherMcpErrors() {
            SentryEvent event = logEvent(SentryConfig.MCP_TRANSPORT_LOGGER,
                    "Unexpected error handling message: boom");

            assertThat(callback.execute(event, new Hint())).isSameAs(event);
        }

        @Test
        @DisplayName("같은 문구라도 MCP 전송 계층이 아닌 로거면 보낸다")
        void keepsMissingHandlerFromOtherLogger() {
            SentryEvent event = logEvent("com.aisip.OnO.backend.mcp.Something",
                    "Missing handler for request type: server/discover");

            assertThat(callback.execute(event, new Hint())).isSameAs(event);
        }

        @Test
        @DisplayName("일반 애플리케이션 error 로그는 그대로 보낸다")
        void keepsApplicationErrorLog() {
            SentryEvent event = logEvent("com.aisip.OnO.backend.problem.service.ProblemService", "S3 업로드 실패");

            assertThat(callback.execute(event, new Hint())).isSameAs(event);
        }

        @Test
        @DisplayName("4xx ApplicationException 은 보내지 않고 5xx 는 보낸다")
        void filtersApplicationExceptionByStatus() {
            SentryEvent clientError = new SentryEvent(new ApplicationException(errorCase(404)));
            SentryEvent serverError = new SentryEvent(new ApplicationException(errorCase(500)));

            assertThat(callback.execute(clientError, new Hint())).isNull();
            assertThat(callback.execute(serverError, new Hint())).isSameAs(serverError);
        }
    }

    @Nested
    @DisplayName("environment 설정")
    class EnvironmentConfiguration {

        @Test
        @DisplayName("prod 프로필은 production 으로 보낸다")
        void prodIsProduction() {
            SentryOptions options = configure(new MockEnvironment(), "prod");

            assertThat(options.getEnvironment()).isEqualTo("production");
            assertThat(options.isEnabled()).isTrue();
        }

        @Test
        @DisplayName("dev 프로필은 development 로 보낸다")
        void devIsDevelopment() {
            SentryOptions options = configure(new MockEnvironment(), "dev");

            assertThat(options.getEnvironment()).isEqualTo("development");
            assertThat(options.isEnabled()).isTrue();
        }

        @Test
        @DisplayName("test 프로필은 DSN 이 있어도 Sentry 를 끈다")
        void testProfileDisablesSentry() {
            SentryOptions options = configure(new MockEnvironment(), "test");

            assertThat(options.isEnabled()).isFalse();
        }

        @Test
        @DisplayName("sentry.environment 를 직접 넣었으면 그 값을 덮어쓰지 않는다")
        void keepsExplicitEnvironment() {
            MockEnvironment environment = new MockEnvironment()
                    .withProperty(SentryConfig.ENVIRONMENT_PROPERTY, "staging");
            SentryOptions options = new SentryOptions();
            options.setEnvironment("staging");
            environment.setActiveProfiles("prod");

            sentryConfig.sentryEnvironmentConfiguration(environment).configure(options);

            assertThat(options.getEnvironment()).isEqualTo("staging");
        }

        @Test
        @DisplayName("프로필이 없으면 local, 그 밖의 프로필은 이름 그대로 쓴다")
        void otherProfiles() {
            assertThat(SentryConfig.resolveEnvironment(new String[]{})).isEqualTo("local");
            assertThat(SentryConfig.resolveEnvironment(new String[]{"local"})).isEqualTo("local");
            assertThat(SentryConfig.resolveEnvironment(new String[]{"monitoring", "prod"})).isEqualTo("production");
        }

        private SentryOptions configure(MockEnvironment environment, String profile) {
            environment.setActiveProfiles(profile);
            SentryOptions options = new SentryOptions();
            Sentry.OptionsConfiguration<SentryOptions> configuration =
                    sentryConfig.sentryEnvironmentConfiguration(environment);
            configuration.configure(options);
            return options;
        }
    }

    @Nested
    @DisplayName("Sentry 자동 설정과 함께 띄웠을 때")
    class WithAutoConfiguration {

        private final ApplicationContextRunner runner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SentryAutoConfiguration.class))
                .withUserConfiguration(SentryConfig.class)
                .withPropertyValues("sentry.dsn=https://key@example.invalid/1");

        @AfterEach
        void closeSentry() {
            Sentry.close();
        }

        @Test
        @DisplayName("dev 프로필이면 development 로 켜지고 기본 beforeSend 등록도 유지된다")
        void devProfile() {
            runner.withPropertyValues("spring.profiles.active=dev").run(context -> {
                SentryProperties properties = context.getBean(SentryProperties.class);

                assertThat(properties.getEnvironment()).isEqualTo("development");
                assertThat(properties.getBeforeSend()).isNotNull();
                assertThat(Sentry.isEnabled()).isTrue();
            });
        }

        @Test
        @DisplayName("test 프로필이면 DSN 이 있어도 Sentry 가 켜지지 않는다")
        void testProfile() {
            runner.withPropertyValues("spring.profiles.active=test").run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(Sentry.isEnabled()).isFalse();
            });
        }
    }

    @Test
    @DisplayName("Gradle 테스트 JVM 에는 SENTRY_DSN 이 넘어오지 않는다")
    void gradleTestTaskRemovesSentryDsn() {
        assertThat(System.getenv("SENTRY_DSN")).isNull();
        assertThat(System.getenv("SENTRY_DSN_DEV")).isNull();
    }

    private static SentryEvent logEvent(String logger, String formatted) {
        SentryEvent event = new SentryEvent();
        Message message = new Message();
        message.setFormatted(formatted);
        event.setMessage(message);
        event.setLogger(logger);
        return event;
    }

    private static ErrorCase errorCase(int status) {
        return new ErrorCase() {
            @Override
            public Integer getHttpStatusCode() {
                return status;
            }

            @Override
            public Integer getErrorCode() {
                return status * 10;
            }

            @Override
            public String getMessage() {
                return "test";
            }
        };
    }
}
