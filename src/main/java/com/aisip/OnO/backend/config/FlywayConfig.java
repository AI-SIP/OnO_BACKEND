package com.aisip.OnO.backend.config;

import org.flywaydb.core.api.MigrationVersion;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlywayConfig {

    @Bean
    public FlywayConfigurationCustomizer flywayConfigurationCustomizer(
            @Value("${spring.flyway.baseline-on-migrate:true}") boolean baselineOnMigrate,
            @Value("${spring.flyway.baseline-version:1}") String baselineVersion,
            @Value("${spring.flyway.baseline-description:Existing schema before Flyway adoption}") String baselineDescription,
            @Value("${spring.flyway.validate-on-migrate:true}") boolean validateOnMigrate,
            @Value("${spring.flyway.clean-disabled:true}") boolean cleanDisabled,
            @Value("${spring.flyway.connect-retries:10}") int connectRetries
    ) {
        return configuration -> configuration
                .baselineOnMigrate(baselineOnMigrate)
                .baselineVersion(MigrationVersion.fromVersion(baselineVersion))
                .baselineDescription(baselineDescription)
                .validateOnMigrate(validateOnMigrate)
                .cleanDisabled(cleanDisabled)
                .connectRetries(connectRetries);
    }
}
