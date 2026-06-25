package org.example.footballleague.config;

import org.flywaydb.core.api.exception.FlywayValidateException;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlywayRecoveryConfig {

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return flyway -> {
            try {
                flyway.migrate();
            } catch (FlywayValidateException ex) {
                if (!isFailedMigrationValidation(ex)) {
                    throw ex;
                }
                flyway.repair();
                flyway.migrate();
            }
        };
    }

    private boolean isFailedMigrationValidation(FlywayValidateException ex) {
        String message = ex.getMessage();
        return message != null && message.contains("Detected failed migration");
    }
}
