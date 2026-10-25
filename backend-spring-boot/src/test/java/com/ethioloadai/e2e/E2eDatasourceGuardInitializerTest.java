package com.ethioloadai.e2e;

import com.ethioloadai.config.CorsProperties;
import com.ethioloadai.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.util.unit.DataSize;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class E2eDatasourceGuardInitializerTest {

    @Test
    void e2eConfigurationImportsMountedSecretsAndRestrictsBrowserOrigins() throws IOException {
        PropertySource<?> e2eProperties = new YamlPropertySourceLoader()
                .load("application-e2e", new ClassPathResource("application-e2e.yml"))
                .get(0);

        assertThat(e2eProperties.getProperty("spring.config.import"))
                .isEqualTo("configtree:/run/secrets/");
        assertThat(e2eProperties.getProperty("cors.allowed-origins"))
                .isEqualTo("http://localhost:5173,http://localhost:5174,http://127.0.0.1:5174,http://127.0.0.1:8765");
    }

    @Test
    void e2eCorsAllowsOnlyTheConfiguredLocalAppOrigins() throws IOException {
        PropertySource<?> e2eProperties = new YamlPropertySourceLoader()
                .load("application-e2e", new ClassPathResource("application-e2e.yml"))
                .get(0);
        CorsProperties corsProperties = new CorsProperties();
        corsProperties.setAllowedOrigins(List.of(
                ((String) e2eProperties.getProperty("cors.allowed-origins")).split(",")));
        SecurityConfig securityConfig = new SecurityConfig(
                null, null, null, null, corsProperties, DataSize.ofMegabytes(1));
        var corsConfiguration = securityConfig.corsConfigurationSource()
                .getCorsConfiguration(new MockHttpServletRequest());

        assertThat(corsConfiguration).isNotNull();
        assertThat(corsConfiguration.checkOrigin("http://localhost:5173"))
                .isEqualTo("http://localhost:5173");
        assertThat(corsConfiguration.checkOrigin("http://localhost:5174"))
                .isEqualTo("http://localhost:5174");
        assertThat(corsConfiguration.checkOrigin("http://127.0.0.1:5174"))
                .isEqualTo("http://127.0.0.1:5174");
        assertThat(corsConfiguration.checkOrigin("http://127.0.0.1:5175")).isNull();
        assertThat(corsConfiguration.checkOrigin("https://127.0.0.1:5174")).isNull();
        assertThat(corsConfiguration.checkOrigin("https://untrusted.example")).isNull();
    }

    @Test
    void acceptsOnlyTheExplicitE2eProfileAndApprovedDatasource() {
        assertThatCode(() -> E2eDatasourceGuardInitializer.validate(e2eEnvironment()))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsDockerNetworkModeWithItsExactDatasource() {
        MockEnvironment environment = e2eEnvironment(
                "docker-network", E2eDatasourceGuardInitializer.ALLOWED_DOCKER_DATASOURCE_URL);

        assertThatCode(() -> E2eDatasourceGuardInitializer.validate(environment))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingOrUnknownDatasourceMode() {
        assertUnsafe(e2eEnvironment(null, APPROVED_URL));
        assertUnsafe(e2eEnvironment("unknown", APPROVED_URL));
    }

    @Test
    void rejectsDatasourceModeAndUrlMismatches() {
        assertUnsafe(e2eEnvironment(
                "host-loopback", E2eDatasourceGuardInitializer.ALLOWED_DOCKER_DATASOURCE_URL));
        assertUnsafe(e2eEnvironment("docker-network", APPROVED_URL));
    }

    @Test
    void rejectsUnexpectedE2eCorsOriginsAndOriginPatterns() {
        MockEnvironment unexpectedOrigin = e2eEnvironment();
        unexpectedOrigin.getPropertySources().addFirst(new MapPropertySource(
                "unexpected-cors-origin",
                Map.of("cors.allowed-origins", "https://untrusted.example")));
        assertUnsafe(unexpectedOrigin);

        MockEnvironment originPattern = e2eEnvironment();
        originPattern.getPropertySources().addFirst(new MapPropertySource(
                "cors-origin-pattern",
                Map.of("cors.allowed-origin-patterns", "*")));
        assertUnsafe(originPattern);
    }

    @Test
    void rejectsConflictingDatasourceModePropertySources() {
        MockEnvironment environment = e2eEnvironment("host-loopback", APPROVED_URL);
        environment.getPropertySources().addFirst(new MapPropertySource(
                "conflicting-mode", Map.of("e2e.datasource.mode", "docker-network")));

        assertUnsafe(environment);

        MockEnvironment environmentAlias = e2eEnvironment("host-loopback", APPROVED_URL);
        environmentAlias.getPropertySources().addFirst(new SystemEnvironmentPropertySource(
                "conflicting-mode-alias",
                Map.of("E2E_DATASOURCE_MODE", "docker-network")));

        assertUnsafe(environmentAlias);
    }

    @Test
    void leavesDevelopmentProfileOutsideTheE2eGuard() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");
        environment.getPropertySources().addFirst(new MapPropertySource(
                "development", Map.of(
                        "spring.datasource.url",
                        "jdbc:postgresql://127.0.0.1:5433/ethioloadai_spring")));

        assertThatCode(() -> E2eDatasourceGuardInitializer.validate(environment))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsUnexpectedActiveProfiles() {
        MockEnvironment environment = e2eEnvironment();
        environment.setActiveProfiles("e2e", "dev");

        assertUnsafe(environment);
    }

    @Test
    void rejectsConflictingProfilePropertySources() {
        MockEnvironment environment = e2eEnvironment();
        environment.getPropertySources().addLast(new MapPropertySource(
                "conflicting-profile", Map.of("spring.profiles.active", "dev")));

        assertUnsafe(environment);
    }

    @Test
    void rejectsIncludedDefaultAndGroupedProfiles() {
        for (Map<String, Object> unsafeProperty : List.<Map<String, Object>>of(
                Map.of("spring.profiles.include", "dev"),
                Map.of("spring.profiles.default", "dev"),
                Map.of("spring.profiles.group.e2e", "dev"))) {
            MockEnvironment environment = e2eEnvironment();
            environment.getPropertySources().addFirst(
                    new MapPropertySource("unsafe-profile", unsafeProperty));
            assertUnsafe(environment);
        }
    }

    @Test
    void rejectsE2eRequestedAsDefaultProfileBeforeGuardCanBeSkipped() {
        MockEnvironment environment = new MockEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource(
                "default-e2e", Map.of("spring.profiles.default", "e2e")));

        assertUnsafe(environment);
    }

    @Test
    void rejectsE2eRequestedThroughIncludeOrAnActiveProfileGroup() {
        MockEnvironment included = new MockEnvironment();
        included.setActiveProfiles("dev");
        included.getPropertySources().addFirst(new MapPropertySource(
                "include-e2e", Map.of("spring.profiles.include", "e2e")));
        assertUnsafe(included);

        MockEnvironment grouped = new MockEnvironment();
        grouped.setActiveProfiles("local");
        grouped.getPropertySources().addFirst(new MapPropertySource(
                "group-e2e", Map.of("spring.profiles.group.local", "e2e")));
        assertUnsafe(grouped);
    }

    @Test
    void rejectsDatasourceUrlParametersAndWrongTargets() {
        for (String url : new String[]{
                ALLOWED_URL_WITH_PARAMETER,
                "jdbc:postgresql://ethioloadai-e2e-db:5432/ethioloadai_e2e?sslmode=disable",
                "jdbc:postgresql://localhost:15433/ethioloadai_e2e",
                "jdbc:postgresql://127.0.0.1:5433/ethioloadai_e2e",
                "jdbc:postgresql://127.0.0.1:15433/another_database"
        }) {
            MockEnvironment environment = e2eEnvironment();
            environment.getPropertySources().addFirst(new MapPropertySource(
                    "unsafe-url", Map.of("spring.datasource.url", url)));
            assertUnsafe(environment);
        }
    }

    @Test
    void rejectsConflictingLowerPriorityDatasourceUrl() {
        MockEnvironment environment = e2eEnvironment();
        environment.getPropertySources().addLast(new MapPropertySource(
                "conflicting-url",
                Map.of("spring.datasource.url",
                        "jdbc:postgresql://127.0.0.1:5433/ethioloadai_spring")));

        assertUnsafe(environment);
    }

    @Test
    void rejectsConflictingDatasourceCredentialsAcrossPropertySources() {
        MockEnvironment environment = e2eEnvironment();
        environment.getPropertySources().addLast(new MapPropertySource(
                "conflicting-username",
                Map.of("spring.datasource.username", "another-user")));

        assertUnsafe(environment);

        MockEnvironment passwordEnvironment = e2eEnvironment();
        passwordEnvironment.getPropertySources().addLast(new MapPropertySource(
                "conflicting-password",
                Map.of("spring.datasource.password", "another-password")));

        assertUnsafe(passwordEnvironment);
    }

    @Test
    void rejectsConflictingDatasourceUrlAliasesWithinOnePropertySource() {
        for (Map<String, Object> aliases : List.<Map<String, Object>>of(
                Map.of(
                        "spring.datasource.url", APPROVED_URL,
                        "spring.dataSource.url", "jdbc:postgresql://elsewhere/other"),
                Map.of(
                        "spring.datasource.url", APPROVED_URL,
                        "SPRING_DATASOURCE_URL", "jdbc:postgresql://elsewhere/other"),
                Map.of(
                        "spring.datasource.url", APPROVED_URL,
                        "spring.datasource.URL", "jdbc:postgresql://elsewhere/other"))) {
            MockEnvironment environment = e2eEnvironment();
            environment.getPropertySources().addFirst(
                    new MapPropertySource("conflicting-url-aliases", aliases));

            assertUnsafe(environment);
        }
    }

    @Test
    void permitsEquivalentDatasourceUrlAliasesWithinOnePropertySource() {
        MockEnvironment environment = e2eEnvironment();
        environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource(
                "equivalent-url-aliases", Map.of(
                        "spring.datasource.url", APPROVED_URL,
                        "SPRING_DATASOURCE_URL", APPROVED_URL,
                        "spring.dataSource.url", APPROVED_URL)));

        assertThatCode(() -> E2eDatasourceGuardInitializer.validate(environment))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsSystemEnvironmentDatasourceAliasesThatDisagree() {
        MockEnvironment environment = e2eEnvironment();
        environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource(
                "conflicting-url-env-aliases", Map.of(
                        "spring.datasource.url", APPROVED_URL,
                        "SPRING_DATASOURCE_URL", "jdbc:postgresql://elsewhere/other")));

        assertUnsafe(environment);
    }

    @Test
    void rejectsAlternateDatasourceAndFlywayTargets() {
        for (String property : new String[]{
                "spring.datasource.hikari.jdbc-url",
                "spring.datasource.hikari.data-source-class-name",
                "spring.datasource.hikari.data-source-properties.serverName",
                "spring.datasource.jndi-name",
                "spring.datasource.type",
                "spring.datasource.embedded-database-connection",
                "spring.datasource.driver-class-name",
                "spring.flyway.url",
                "spring.flyway.user",
                "spring.flyway.password",
                "spring.flyway.driver-class-name",
                "spring.flyway.enabled"
        }) {
            MockEnvironment environment = e2eEnvironment();
            environment.getPropertySources().addFirst(
                    new MapPropertySource("unsafe-target", Map.of(property, "configured")));
            assertUnsafe(environment);
        }
    }

    @Test
    void rejectsHikariEnvironmentVariableAliases() {
        MockEnvironment jdbcUrl = e2eEnvironment();
        SystemEnvironmentPropertySource jdbcUrlEnvironment = new SystemEnvironmentPropertySource(
                "hikari-jdbc-url-env",
                Map.of("SPRING_DATASOURCE_HIKARI_JDBC_URL",
                        "jdbc:postgresql://elsewhere/other"));
        jdbcUrl.getPropertySources().addFirst(jdbcUrlEnvironment);
        assertThat(jdbcUrl.getProperty("spring.datasource.hikari.jdbc-url"))
                .isEqualTo("jdbc:postgresql://elsewhere/other");
        assertUnsafe(jdbcUrl);

        MockEnvironment driverProperties = e2eEnvironment();
        SystemEnvironmentPropertySource driverPropertiesEnvironment =
                new SystemEnvironmentPropertySource(
                        "hikari-driver-properties-env",
                        Map.of("SPRING_DATASOURCE_HIKARI_DATA_SOURCE_PROPERTIES_HOST",
                                "elsewhere"));
        driverProperties.getPropertySources().addFirst(driverPropertiesEnvironment);
        assertThat(driverProperties.getProperty(
                "spring.datasource.hikari.data-source-properties.host")).isEqualTo("elsewhere");
        assertUnsafe(driverProperties);
    }

    @Test
    void rejectsEnvironmentAliasesForHikariAndFlywayTargets() {
        for (String property : new String[]{
                "SPRING_DATASOURCE_HIKARI_JDBC_URL",
                "SPRING_DATASOURCE_HIKARI_DATA_SOURCE_CLASS_NAME",
                "SPRING_DATASOURCE_JNDI_NAME",
                "SPRING_FLYWAY_URL",
                "SPRING_FLYWAY_USER",
                "SPRING_FLYWAY_PASSWORD"
        }) {
            MockEnvironment environment = e2eEnvironment();
            environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource(
                    "alternate-target-env", Map.of(property, "configured")));

            assertUnsafe(environment);
        }
    }

    @Test
    void rejectsCustomFlywayAndSqlInitialization() {
        MockEnvironment customFlyway = e2eEnvironment();
        customFlyway.getPropertySources().addFirst(new MapPropertySource(
                "custom-flyway", Map.of("flyway.custom.enabled", "true")));
        assertUnsafe(customFlyway);

        MockEnvironment sqlInitialization = e2eEnvironment();
        sqlInitialization.getPropertySources().addFirst(new MapPropertySource(
                "sql-init", Map.of("spring.sql.init.mode", "always")));
        assertUnsafe(sqlInitialization);
    }

    @Test
    void rejectsMissingDatasourceCredentials() {
        MockEnvironment environment = e2eEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource(
                "missing-password", Map.of(
                        "spring.datasource.url", E2eDatasourceGuardInitializer.ALLOWED_DATASOURCE_URL,
                        "spring.datasource.username", "synthetic",
                        "spring.datasource.password", "")));

        assertUnsafe(environment);
    }

    @Test
    void rejectsEnablingOutboundAiOrRoutingServices() {
        for (String property : new String[]{
                "ai-engine.enabled",
                "routing.external-services.enabled",
                "AI_ENGINE_ENABLED",
                "ROUTING_EXTERNAL_SERVICES_ENABLED"
        }) {
            MockEnvironment environment = e2eEnvironment();
            PropertySource<?> override = property.equals(property.toUpperCase(Locale.ROOT))
                    ? new SystemEnvironmentPropertySource(
                            "external-service-enabled", Map.of(property, "true"))
                    : new MapPropertySource(
                            "external-service-enabled", Map.of(property, "true"));
            environment.getPropertySources().addFirst(override);

            assertUnsafe(environment);
        }
    }

    @Test
    void allowsNonE2eAiDefaultWhenE2eExplicitlyDisablesIt() {
        MockEnvironment environment = e2eEnvironment();
        environment.getPropertySources().addLast(new MapPropertySource(
                "base-defaults", Map.of("ai-engine.enabled", "true")));

        assertThatCode(() -> E2eDatasourceGuardInitializer.validate(environment))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsMissingOrBlankSmtpSettings() {
        MockEnvironment missingSettings = e2eEnvironment();
        assertThatCode(() -> E2eDatasourceGuardInitializer.validate(missingSettings))
                .doesNotThrowAnyException();

        MockEnvironment blankSettings = e2eEnvironment();
        blankSettings.getPropertySources().addFirst(new MapPropertySource(
                "blank-smtp", Map.of(
                        "spring.mail.username", "",
                        "spring.mail.password", "  ",
                        "carrier.invitations.email-from", "")));
        assertThatCode(() -> E2eDatasourceGuardInitializer.validate(blankSettings))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsEffectiveNonblankSmtpSettingsAndEnvironmentAliases() {
        for (String property : new String[]{
                "spring.mail.username",
                "spring.mail.password",
                "carrier.invitations.email-from"
        }) {
            MockEnvironment environment = e2eEnvironment();
            environment.getPropertySources().addFirst(new MapPropertySource(
                    "smtp-override", Map.of(property, "configured")));
            assertUnsafe(environment);
        }

        MockEnvironment environmentAliases = e2eEnvironment();
        environmentAliases.getPropertySources().addFirst(new SystemEnvironmentPropertySource(
                "smtp-environment-aliases", Map.of(
                        "SPRING_MAIL_USERNAME", "configured",
                        "SPRING_MAIL_PASSWORD", "configured",
                        "CARRIER_INVITATIONS_EMAIL_FROM", "configured")));
        assertUnsafe(environmentAliases);
    }

    private static MockEnvironment e2eEnvironment() {
        return e2eEnvironment("host-loopback", E2eDatasourceGuardInitializer.ALLOWED_DATASOURCE_URL);
    }

    private static MockEnvironment e2eEnvironment(String mode, String datasourceUrl) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("e2e");
        Map<String, Object> properties = new HashMap<>(Map.of(
                "spring.profiles.active", "e2e",
                "spring.datasource.url", datasourceUrl,
                "spring.datasource.driver-class-name", "org.postgresql.Driver",
                "spring.datasource.username", "synthetic-user",
                "spring.datasource.password", "synthetic-password",
                "ai-engine.enabled", "false",
                "routing.external-services.enabled", "false",
                "cors.allowed-origins",
                "http://localhost:5173,http://localhost:5174,http://127.0.0.1:5174,http://127.0.0.1:8765",
                "spring.flyway.enabled", "true",
                "spring.sql.init.mode", "never"));
        if (mode != null) {
            properties.put("e2e.datasource.mode", mode);
        }
        environment.getPropertySources().addFirst(new MapPropertySource(
                "e2e-properties", properties));
        return environment;
    }

    private static final String APPROVED_URL =
            E2eDatasourceGuardInitializer.ALLOWED_DATASOURCE_URL;

    private static void assertUnsafe(MockEnvironment environment) {
        assertThatThrownBy(() -> E2eDatasourceGuardInitializer.validate(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unsafe E2E datasource configuration");
    }

    private static final String ALLOWED_URL_WITH_PARAMETER =
            "jdbc:postgresql://127.0.0.1:15433/ethioloadai_e2e?sslmode=disable";

}
