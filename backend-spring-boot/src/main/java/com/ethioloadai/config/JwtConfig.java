package com.ethioloadai.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.nio.charset.StandardCharsets;

@Data
@Configuration
@ConfigurationProperties(prefix = "jwt")
@Validated
public class JwtConfig {

    private static final Logger log = LoggerFactory.getLogger(JwtConfig.class);

    @NotBlank(message = "JWT secret must be configured")
    private String secret;

    @NotNull(message = "JWT expiration must be configured")
    @Positive(message = "JWT expiration must be positive")
    private Long expiration;

    @NotNull(message = "JWT refresh expiration must be configured")
    @Positive(message = "JWT refresh expiration must be positive")
    private Long refreshExpiration;

    @NotBlank(message = "JWT header must be configured")
    private String header;

    @NotBlank(message = "JWT prefix must be configured")
    private String prefix;

    /**
     * HMAC-SHA256 requires a key of at least 256 bits (32 bytes).
     *
     * <p>We measure the UTF-8 byte length of the secret string, not the Java
     * {@code char} count. A Java {@code String.length()} call counts UTF-16
     * code units, which can differ from the actual UTF-8 byte count when the
     * string contains multi-byte characters. Measuring bytes ensures the
     * derived key truly meets the cryptographic minimum regardless of the
     * character set used.
     */
    @PostConstruct
    public void validate() {
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException(
                "JWT secret must be at least 32 bytes (256 bits) when encoded as UTF-8. " +
                "Current size: " + secretBytes.length + " bytes (" + secret.length() + " chars). " +
                "Please set a secure JWT_SECRET environment variable."
            );
        }
        log.info("JWT configuration validated — secret is {} bytes, expiration {}ms, refresh {}ms",
                secretBytes.length, expiration, refreshExpiration);
    }
}
