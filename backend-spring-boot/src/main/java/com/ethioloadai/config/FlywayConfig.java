package com.ethioloadai.config;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Custom Flyway configuration bean.
 *
 * Only activates when {@code flyway.custom.enabled=true} (defaults to false).
 * In normal operation, Spring Boot's built-in Flyway auto-configuration is used
 * instead (configured via spring.flyway.* in application-dev.yml).
 *
 * This bean exists for scenarios where repair + migrate must happen in a specific
 * order outside of Spring Boot's standard Flyway initialization.
 */
@Configuration
@ConditionalOnProperty(name = "flyway.custom.enabled", havingValue = "true", matchIfMissing = false)
public class FlywayConfig {

    @Value("${flyway.repair:true}")
    private boolean repairBeforeMigrate;

    @Bean
    public Flyway flyway(DataSource dataSource) {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .baselineOnMigrate(true)
                .locations("classpath:db/migration")
                .ignoreMigrationPatterns("*:missing", "*:future")
                .load();
        if (repairBeforeMigrate) {
            flyway.repair();
        }
        flyway.migrate();
        return flyway;
    }
}
