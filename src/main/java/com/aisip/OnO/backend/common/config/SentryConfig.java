package com.aisip.OnO.backend.common.config;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import io.sentry.Sentry;
import io.sentry.SentryEvent;
import io.sentry.SentryOptions;
import io.sentry.protocol.Message;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import java.util.Arrays;
import java.util.List;

@Configuration
public class SentryConfig {

    static final String ENVIRONMENT_PROPERTY = "sentry.environment";

    // 5xx 마다 남기는 접근 로그. 원래 예외 이벤트가 따로 올라가므로 Sentry 에서는 중복 이슈가 된다.
    static final String REQUEST_LOG_LOGGER = RequestLoggingMdcFilter.class.getName();

    // MCP 클라이언트가 지원하지 않는 메서드(server/discover 등)를 보내면 SDK 가 error 로 남긴다.
    static final String MCP_TRANSPORT_LOGGER =
            "io.modelcontextprotocol.server.transport.WebMvcStatelessServerTransport";
    static final String MCP_MISSING_HANDLER_MESSAGE = "Missing handler for request type";

    @Bean
    public SentryOptions.BeforeSendCallback sentryBeforeSendCallback() {
        return (event, hint) -> shouldDrop(event) ? null : event;
    }

    /**
     * 기본 sentryOptionsConfiguration 빈(beforeSend 등록)과 별개로, 그 뒤에 환경 값을 덮어쓴다.
     * application-dev.yml, application-prod.yml 은 GitHub secret 으로 만들어져 코드에서 고칠 수 없어서
     * 활성 프로필로 environment 를 정한다. yml 이나 SENTRY_ENVIRONMENT 로 직접 넣은 값이 있으면 그 값을 따른다.
     */
    @Bean
    public Sentry.OptionsConfiguration<SentryOptions> sentryEnvironmentConfiguration(Environment environment) {
        return options -> {
            if (environment.acceptsProfiles(Profiles.of("test"))) {
                // 실행 환경에 SENTRY_DSN 이 있어도 테스트가 의도한 예외를 Sentry 로 보내지 않는다.
                options.setEnabled(false);
                return;
            }
            if (!environment.containsProperty(ENVIRONMENT_PROPERTY)) {
                options.setEnvironment(resolveEnvironment(environment.getActiveProfiles()));
            }
        };
    }

    static String resolveEnvironment(String[] activeProfiles) {
        List<String> profiles = Arrays.asList(activeProfiles);
        if (profiles.contains("prod")) {
            return "production";
        }
        if (profiles.contains("dev")) {
            return "development";
        }
        if (profiles.isEmpty()) {
            return "local";
        }
        return profiles.get(0);
    }

    static boolean shouldDrop(SentryEvent event) {
        Throwable throwable = event.getThrowable();
        if (throwable instanceof ApplicationException appEx
                && appEx.getErrorCase().getHttpStatusCode() < 500) {
            return true;
        }

        String logger = event.getLogger();
        if (REQUEST_LOG_LOGGER.equals(logger)) {
            return true;
        }
        return MCP_TRANSPORT_LOGGER.equals(logger) && isMcpMissingHandler(event);
    }

    private static boolean isMcpMissingHandler(SentryEvent event) {
        Message message = event.getMessage();
        if (message != null && containsMissingHandler(message.getFormatted())) {
            return true;
        }
        Throwable throwable = event.getThrowable();
        return throwable != null && containsMissingHandler(throwable.getMessage());
    }

    private static boolean containsMissingHandler(String text) {
        return text != null && text.contains(MCP_MISSING_HANDLER_MESSAGE);
    }
}
