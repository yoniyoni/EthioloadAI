package com.ethioloadai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * CORS configuration properties bound from {@code cors.*} in application YAML/environment.
 *
 * <p>Set {@code cors.allowed-origins} to a comma-separated list of allowed origins.
 * In production supply origins via the {@code CORS_ALLOWED_ORIGINS} environment variable,
 * which overrides the YAML default.
 *
 * <p>Example environment override:
 * <pre>CORS_ALLOWED_ORIGINS=https://app.ethioloadai.com,https://admin.ethioloadai.com</pre>
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "cors")
public class CorsProperties {

    /**
     * Allowed origins for CORS requests.
     * Defaults to localhost development ports; always override in production.
     */
    private List<String> allowedOrigins = List.of(
        "http://localhost:3000",
        "http://localhost:8080"
    );

    /**
     * Allowed origin patterns for CORS requests.
     * Useful in development for dynamic ports (e.g. Flutter Web) while supporting credentials.
     */
    private List<String> allowedOriginPatterns = List.of();
}

