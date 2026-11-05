package com.ethioloadai.e2e;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class E2eDatasourceGuardInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    static final String ALLOWED_DATASOURCE_URL =
            "jdbc:postgresql://127.0.0.1:15433/ethioloadai_e2e";
    static final String ALLOWED_DOCKER_DATASOURCE_URL =
            "jdbc:postgresql://ethioloadai-e2e-db:5432/ethioloadai_e2e";

    private static final String E2E_PROFILE = "e2e";
    private static final String DATASOURCE_MODE_PROPERTY = "e2e.datasource.mode";
    private static final String MAIL_CAPTURE_ENABLED_PROPERTY = "e2e.mail.capture.enabled";
    private static final String MAIL_CAPTURE_SENDER_PROPERTY = "e2e.mail.capture.sender";
    private static final String MAIL_CAPTURE_SMTP_HOST = "ethioloadai-e2e-mail-capture";
    private static final String MAIL_CAPTURE_SMTP_PORT = "12525";
    private static final String MAIL_CAPTURE_SENDER = "ethioloadai-test@example.test";
    private static final Set<String> ALLOWED_E2E_SMTP_PROPERTIES = Set.of(
            "springmailpropertiesmailsmtpauth",
            "springmailpropertiesmailsmtpsslenable",
            "springmailpropertiesmailsmtpstarttlsenable");

    private static final String[] ALTERNATE_DATASOURCE_PROPERTIES = {
            "spring.datasource.hikari.jdbc-url",
            "spring.datasource.hikari.jdbcUrl",
            "spring.datasource.hikari.data-source-class-name",
            "spring.datasource.hikari.dataSourceClassName",
            "spring.datasource.hikari.data-source-properties",
            "spring.datasource.hikari.dataSourceProperties",
            "spring.datasource.jndi-name",
            "spring.datasource.jndiName",
            "spring.datasource.type",
            "spring.datasource.name",
            "spring.datasource.embedded-database-connection",
            "spring.datasource.generate-unique-name"
    };

    private static final String[] ALTERNATE_FLYWAY_PROPERTIES = {
            "spring.flyway.url",
            "spring.flyway.driver-class-name",
            "spring.flyway.jndi-name"
    };

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        validate(applicationContext.getEnvironment());
    }

    static void validate(ConfigurableEnvironment environment) {
        if (!isE2eProfileRequestedOrEffective(environment)) {
            return;
        }

        if (!contains(environment.getActiveProfiles(), E2E_PROFILE)) {
            throw unsafeConfiguration("e2e must be explicitly active");
        }

        requireExactE2eProfiles(environment);
        String datasourceMode = environment.getProperty(DATASOURCE_MODE_PROPERTY);
        if (!"host-loopback".equals(datasourceMode)
                && !"docker-network".equals(datasourceMode)) {
            throw unsafeConfiguration(
                    DATASOURCE_MODE_PROPERTY + " must be host-loopback or docker-network");
        }
        requireExactPropertyValue(environment, DATASOURCE_MODE_PROPERTY, datasourceMode);
        String allowedDatasourceUrl = "host-loopback".equals(datasourceMode)
                ? ALLOWED_DATASOURCE_URL
                : ALLOWED_DOCKER_DATASOURCE_URL;
        requireExactPropertyValue(environment, "spring.datasource.url", allowedDatasourceUrl);
        requireExactPropertyValue(
                environment, "spring.datasource.driver-class-name", "org.postgresql.Driver");
        requireConfiguredProperty(environment, "spring.datasource.username");
        requireConfiguredProperty(environment, "spring.datasource.password");
        requireConfiguredProperty(environment, "spring.flyway.user");
        requireConfiguredProperty(environment, "spring.flyway.password");
        requireDistinctCredentials(
                environment.getProperty("spring.datasource.username"),
                environment.getProperty("spring.datasource.password"),
                environment.getProperty("spring.flyway.user"),
                environment.getProperty("spring.flyway.password"));
        requireEffectivePropertyValue(environment, "ai-engine.enabled", "false");
        requireEffectivePropertyValue(environment, "routing.external-services.enabled", "false");
        requireEffectivePropertyValue(
                environment,
                "cors.allowed-origins",
                "http://localhost:5173,http://localhost:5174,http://127.0.0.1:5174,http://127.0.0.1:8765");
        requirePropertyAbsent(environment, new String[]{"cors.allowed-origin-patterns"});
        validateE2eMailConfiguration(environment);
        requirePropertyAbsent(environment, ALTERNATE_DATASOURCE_PROPERTIES);
        requirePropertyPrefixAbsent(environment, "spring.datasource.hikari.data-source-properties.");
        requirePropertyAbsent(environment, ALTERNATE_FLYWAY_PROPERTIES);
        requireCustomFlywayDisabled(environment);
        requireExactPropertyValue(environment, "spring.flyway.enabled", "true");
        requireExactPropertyValue(environment, "spring.sql.init.mode", "never");
        requirePropertyPrefixAbsent(environment, "spring.sql.init.schema-locations");
        requirePropertyPrefixAbsent(environment, "spring.sql.init.data-locations");
        requirePropertyAbsent(environment, new String[]{
                "spring.sql.init.schema",
                "spring.sql.init.data",
                "spring.sql.init.username",
                "spring.sql.init.password"
        });
    }

    private static boolean isE2eProfileRequestedOrEffective(
            ConfigurableEnvironment environment) {
        if (contains(environment.getActiveProfiles(), E2E_PROFILE)
                || contains(environment.getDefaultProfiles(), E2E_PROFILE)) {
            return true;
        }

        for (String property : new String[]{
                "spring.profiles.active",
                "spring.profiles.default",
                "spring.profiles.include"
        }) {
            for (Object value : propertyValues(environment, property)) {
                if (containsProfile(environment.resolvePlaceholders(String.valueOf(value)),
                        E2E_PROFILE)) {
                    return true;
                }
            }
        }

        return activeProfileGroupIncludesE2e(environment);
    }

    private static boolean activeProfileGroupIncludesE2e(
            ConfigurableEnvironment environment) {
        List<String> selectedProfiles = new ArrayList<>();
        selectedProfiles.addAll(List.of(environment.getActiveProfiles()));
        selectedProfiles.addAll(List.of(environment.getDefaultProfiles()));

        for (PropertySource<?> propertySource : environment.getPropertySources()) {
            if (profileGroupIncludesE2e(propertySource, selectedProfiles)) {
                return true;
            }
        }
        return false;
    }

    private static boolean profileGroupIncludesE2e(
            PropertySource<?> propertySource,
            List<String> selectedProfiles) {
        if (propertySource instanceof CompositePropertySource composite) {
            for (PropertySource<?> nested : composite.getPropertySources()) {
                if (profileGroupIncludesE2e(nested, selectedProfiles)) {
                    return true;
                }
            }
        } else if (propertySource instanceof EnumerablePropertySource<?> enumerable) {
            for (String name : enumerable.getPropertyNames()) {
                String normalizedName = normalizePropertyName(name);
                String groupPrefix = normalizePropertyName("spring.profiles.group.");
                if (!normalizedName.startsWith(groupPrefix)) {
                    continue;
                }

                String groupName = normalizedName.substring(groupPrefix.length());
                if (selectedProfiles.stream()
                        .noneMatch(profile -> normalizePropertyName(profile).equals(groupName))) {
                    continue;
                }

                Object value = propertySource.getProperty(name);
                if (value != null
                        && containsProfile(String.valueOf(value), E2E_PROFILE)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean containsProfile(String value, String expectedProfile) {
        for (String profile : value.split("[,\\s]+")) {
            if (expectedProfile.equals(profile.trim())) {
                return true;
            }
        }
        return false;
    }

    private static void requireExactE2eProfiles(ConfigurableEnvironment environment) {
        String[] activeProfiles = environment.getActiveProfiles();
        if (activeProfiles.length != 1 || !E2E_PROFILE.equals(activeProfiles[0])) {
            throw unsafeConfiguration("exactly the e2e profile must be active");
        }

        requireEveryPropertySourceValue(
                environment, "spring.profiles.active", E2E_PROFILE);
        requirePropertyAbsent(environment, new String[]{
                "spring.profiles.include",
                "spring.profiles.default"
        });
        requirePropertyPrefixAbsent(environment, "spring.profiles.group.");
    }

    private static void requireExactPropertyValue(
            ConfigurableEnvironment environment,
            String property,
            String expected) {
        requireEffectivePropertyValue(environment, property, expected);
        requireEveryPropertySourceValue(environment, property, expected);
    }

    private static void requireEffectivePropertyValue(
            ConfigurableEnvironment environment,
            String property,
            String expected) {
        String value = environment.getProperty(property);
        if (!expected.equals(value)) {
            throw unsafeConfiguration(property + " must match the approved E2E value");
        }
    }

    private static void requireConfiguredProperty(
            ConfigurableEnvironment environment,
            String property) {
        String value = environment.getProperty(property);
        if (value == null || value.isBlank()) {
            throw unsafeConfiguration(property + " must be configured");
        }
        requireEveryPropertySourceValue(environment, property, value);
    }

    private static void requireDistinctCredentials(
            String datasourceUsername,
            String datasourcePassword,
            String flywayUsername,
            String flywayPassword) {
        if (datasourceUsername.equals(flywayUsername)
                || datasourcePassword.equals(flywayPassword)) {
            throw unsafeConfiguration(
                    "runtime and Flyway credentials must be distinct");
        }
    }

    private static void requireBlankProperty(
            ConfigurableEnvironment environment,
            String property) {
        String value = environment.getProperty(property);
        if (value != null && !value.isBlank()) {
            throw unsafeConfiguration(property + " must be blank for E2E");
        }
        if (value != null) {
            requireEveryPropertySourceValue(environment, property, value);
        }
    }

    private static void validateE2eMailConfiguration(ConfigurableEnvironment environment) {
        String configuredCaptureEnabled = environment.getProperty(MAIL_CAPTURE_ENABLED_PROPERTY);
        String captureEnabled = configuredCaptureEnabled == null
                ? "false"
                : configuredCaptureEnabled;
        if (!"true".equals(captureEnabled) && !"false".equals(captureEnabled)) {
            throw unsafeConfiguration(MAIL_CAPTURE_ENABLED_PROPERTY + " must be true or false");
        }
        if (configuredCaptureEnabled != null) {
            requireEffectivePropertyValue(
                    environment, MAIL_CAPTURE_ENABLED_PROPERTY, captureEnabled);
            requireEveryPropertySourceValue(
                    environment, MAIL_CAPTURE_ENABLED_PROPERTY, captureEnabled);
        }
        requireExactPropertyValue(environment, "spring.mail.host", MAIL_CAPTURE_SMTP_HOST);
        requireExactPropertyValue(environment, "spring.mail.port", MAIL_CAPTURE_SMTP_PORT);
        requireExactPropertyValue(environment, "spring.mail.properties.mail.smtp.auth", "false");
        requireExactPropertyValue(environment, "spring.mail.properties.mail.smtp.ssl.enable", "false");
        requireExactPropertyValue(
                environment, "spring.mail.properties.mail.smtp.starttls.enable", "false");
        requireBlankProperty(environment, "spring.mail.username");
        requireBlankProperty(environment, "spring.mail.password");
        requirePropertyAbsent(environment, new String[]{
                "spring.mail.jndi-name",
                "spring.mail.protocol",
                "spring.mail.test-connection"
        });
        rejectUnapprovedSmtpProperties(environment);

        if (Boolean.parseBoolean(captureEnabled)) {
            requireExactPropertyValue(
                    environment, MAIL_CAPTURE_SENDER_PROPERTY, MAIL_CAPTURE_SENDER);
            requireExactPropertyValue(
                    environment, "carrier.invitations.email-from", MAIL_CAPTURE_SENDER);
        } else {
            requireBlankProperty(environment, MAIL_CAPTURE_SENDER_PROPERTY);
            requireBlankProperty(environment, "carrier.invitations.email-from");
        }
    }

    private static void rejectUnapprovedSmtpProperties(ConfigurableEnvironment environment) {
        for (PropertySource<?> propertySource : environment.getPropertySources()) {
            rejectUnapprovedSmtpProperties(propertySource);
        }
    }

    private static void rejectUnapprovedSmtpProperties(PropertySource<?> propertySource) {
        if (propertySource instanceof CompositePropertySource composite) {
            for (PropertySource<?> nested : composite.getPropertySources()) {
                rejectUnapprovedSmtpProperties(nested);
            }
        } else if (propertySource instanceof EnumerablePropertySource<?> enumerable) {
            for (String name : enumerable.getPropertyNames()) {
                String normalizedName = normalizePropertyName(name);
                if (normalizedName.startsWith("springmailpropertiesmailsmtp")
                        && !ALLOWED_E2E_SMTP_PROPERTIES.contains(normalizedName)) {
                    throw unsafeConfiguration(
                            "alternate SMTP configuration is not allowed: " + name);
                }
            }
        }
    }

    private static void requireCustomFlywayDisabled(ConfigurableEnvironment environment) {
        String enabled = environment.getProperty("flyway.custom.enabled");
        if (Boolean.parseBoolean(enabled)) {
            throw unsafeConfiguration("custom Flyway configuration must be disabled");
        }
        if (enabled != null) {
            requireEveryPropertySourceValue(environment, "flyway.custom.enabled", enabled);
        }
    }

    private static void requireEveryPropertySourceValue(
            ConfigurableEnvironment environment,
            String property,
            String expected) {
        for (Object value : propertyValues(environment, property)) {
            String resolved = environment.resolvePlaceholders(String.valueOf(value));
            if (!expected.equals(resolved)) {
                throw unsafeConfiguration("conflicting property sources define " + property);
            }
        }
    }

    private static void requirePropertyAbsent(
            ConfigurableEnvironment environment,
            String[] properties) {
        for (String property : properties) {
            if (!propertyValues(environment, property).isEmpty()
                    || propertyNameExists(environment, property)) {
                throw unsafeConfiguration("alternate configuration is not allowed: " + property);
            }
        }
    }

    private static void requirePropertyPrefixAbsent(
            ConfigurableEnvironment environment,
            String prefix) {
        for (PropertySource<?> propertySource : environment.getPropertySources()) {
            if (containsPropertyPrefix(propertySource, normalizePropertyName(prefix))) {
                throw unsafeConfiguration("alternate configuration is not allowed: " + prefix);
            }
        }
    }

    private static boolean containsPropertyPrefix(PropertySource<?> propertySource, String prefix) {
        if (propertySource instanceof CompositePropertySource composite) {
            for (PropertySource<?> nested : composite.getPropertySources()) {
                if (containsPropertyPrefix(nested, prefix)) {
                    return true;
                }
            }
        } else if (propertySource instanceof EnumerablePropertySource<?> enumerable) {
            for (String name : enumerable.getPropertyNames()) {
                if (normalizePropertyName(name).startsWith(prefix)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean propertyNameExists(
            ConfigurableEnvironment environment,
            String property) {
        for (PropertySource<?> propertySource : environment.getPropertySources()) {
            if (containsPropertyName(propertySource, normalizePropertyName(property))) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsPropertyName(
            PropertySource<?> propertySource,
            String normalizedName) {
        if (propertySource instanceof CompositePropertySource composite) {
            for (PropertySource<?> nested : composite.getPropertySources()) {
                if (containsPropertyName(nested, normalizedName)) {
                    return true;
                }
            }
        } else if (propertySource instanceof EnumerablePropertySource<?> enumerable) {
            for (String name : enumerable.getPropertyNames()) {
                if (normalizePropertyName(name).equals(normalizedName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String normalizePropertyName(String name) {
        return name.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
    }

    private static List<Object> propertyValues(
            ConfigurableEnvironment environment,
            String property) {
        List<Object> values = new ArrayList<>();
        for (PropertySource<?> propertySource : environment.getPropertySources()) {
            collectPropertyValues(
                    propertySource, normalizePropertyName(property), property, values);
        }
        return values;
    }

    private static void collectPropertyValues(
            PropertySource<?> propertySource,
            String normalizedProperty,
            String property,
            List<Object> values) {
        if (propertySource instanceof CompositePropertySource composite) {
            for (PropertySource<?> nested : composite.getPropertySources()) {
                collectPropertyValues(nested, normalizedProperty, property, values);
            }
        } else if (propertySource instanceof EnumerablePropertySource<?> enumerable) {
            boolean foundAlias = false;
            for (String name : enumerable.getPropertyNames()) {
                if (normalizePropertyName(name).equals(normalizedProperty)) {
                    values.add(propertySource.getProperty(name));
                    foundAlias = true;
                }
            }
            if (!foundAlias && propertySource.containsProperty(property)) {
                values.add(propertySource.getProperty(property));
            }
        } else if (propertySource.containsProperty(property)) {
            values.add(propertySource.getProperty(property));
        }
    }

    private static boolean contains(String[] values, String expected) {
        for (String value : values) {
            if (expected.equals(value)) {
                return true;
            }
        }
        return false;
    }

    private static IllegalStateException unsafeConfiguration(String reason) {
        return new IllegalStateException("Unsafe E2E datasource configuration: " + reason);
    }
}
