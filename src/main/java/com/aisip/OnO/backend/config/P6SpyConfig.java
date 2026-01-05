package com.aisip.OnO.backend.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class P6SpyConfig {

    @Value("${spring.profiles.active:local}")
    private String activeProfile;

    private final DataSource dataSource;

    @PostConstruct
    public void setExplainEnabled() {
        // 로컬 환경에서만 EXPLAIN 활성화
        boolean enableExplain = "local".equals(activeProfile);
        System.setProperty("p6spy.enable.explain", String.valueOf(enableExplain));

        log.info("P6Spy EXPLAIN enabled: {} (active profile: {})", enableExplain, activeProfile);

        // DataSource를 P6SpyExplainAppender에 전달
        P6SpyExplainAppender.setDataSource(dataSource);
        log.info("P6Spy DataSource configured");
    }
}
