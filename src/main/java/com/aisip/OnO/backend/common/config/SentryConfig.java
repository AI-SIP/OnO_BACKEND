package com.aisip.OnO.backend.common.config;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import io.sentry.SentryOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SentryConfig {

    @Bean
    public SentryOptions.BeforeSendCallback sentryBeforeSendCallback() {
        return (event, hint) -> {
            Throwable throwable = event.getThrowable();
            if (throwable instanceof ApplicationException appEx) {
                if (appEx.getErrorCase().getHttpStatusCode() < 500) {
                    return null;
                }
            }
            return event;
        };
    }
}
