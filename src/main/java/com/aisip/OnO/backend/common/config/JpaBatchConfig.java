package com.aisip.OnO.backend.common.config;

import org.hibernate.cfg.AvailableSettings;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class JpaBatchConfig {

    @Bean
    public HibernatePropertiesCustomizer hibernateBatchCustomizer() {
        return hibernateProperties -> hibernateProperties.putAll(Map.of(
                AvailableSettings.STATEMENT_BATCH_SIZE, "50",
                AvailableSettings.ORDER_INSERTS, "true",
                AvailableSettings.ORDER_UPDATES, "true"
        ));
    }
}
