package com.ethioloadai.e2e;

import java.util.Map;

public final class PostgresConcurrencyDatasourceGuard {

    static final String APPROVED_URL =
            "jdbc:postgresql://127.0.0.1:15434/ethioloadai_dispatch_disposable";
    static final String APPROVED_TARGET =
            "ethioloadai_dispatch_disposable@127.0.0.1:15434";

    private PostgresConcurrencyDatasourceGuard() {}

    public static void validate() {
        validateConfiguration(
                System.getProperty("ethioloadai.postgres-concurrency"),
                System.getProperty("ethioloadai.postgres-concurrency.approved-disposable"),
                System.getenv("ETHIOLOADAI_POSTGRES_TEST_URL"),
                System.getenv("ETHIOLOADAI_POSTGRES_TEST_DISPOSABLE_TARGET"),
                System.getenv("ETHIOLOADAI_POSTGRES_TEST_USERNAME"),
                System.getenv("ETHIOLOADAI_POSTGRES_TEST_PASSWORD"),
                System.getenv());
        for (String propertyName : System.getProperties().stringPropertyNames()) {
            if (isExternalSpringOverride(propertyName)) {
                throw unsafe("external Spring datasource or Flyway overrides are not allowed");
            }
        }
    }

    static void validateConfiguration(
            String enabled,
            String approved,
            String url,
            String target,
            String username,
            String password,
            Map<String, String> environment) {
        if (!"true".equals(enabled) || !"true".equals(approved)) {
            throw unsafe("the destructive PostgreSQL test requires two explicit opt-ins");
        }
        if (!APPROVED_URL.equals(url) || !APPROVED_TARGET.equals(target)) {
            throw unsafe("the destructive PostgreSQL test target is not the approved disposable database");
        }
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw unsafe("dedicated PostgreSQL test credentials must be configured");
        }
        for (String propertyName : environment.keySet()) {
            if (isExternalSpringOverride(propertyName)) {
                throw unsafe("external Spring datasource or Flyway overrides are not allowed");
            }
        }
    }

    private static boolean isExternalSpringOverride(String propertyName) {
        String normalized = propertyName.replaceAll("[^A-Za-z0-9]", "")
                .toLowerCase(java.util.Locale.ROOT);
        return normalized.startsWith("springdatasource")
                || normalized.startsWith("springflyway")
                || normalized.equals("springjpahibernateddlauto");
    }

    private static IllegalStateException unsafe(String reason) {
        return new IllegalStateException("Unsafe PostgreSQL concurrency-test datasource: " + reason);
    }
}
