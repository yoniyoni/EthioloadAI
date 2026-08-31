package com.ethioloadai.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Slf4j
@Data
@Configuration
@ConfigurationProperties(prefix = "jwt")
@Validated
public class JwtConfig {
    
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

    @PostConstruct
    public void validate() {
        if (secret.length() < 32) {
            throw new IllegalStateException(
                "JWT secret must be at least 32 characters (256 bits) for security. " +
                "Current length: " + secret.length() + " characters. " +
                "Please set a secure JWT_SECRET environment variable."
            );
        }
        log.info("JWT configuration validated successfully");
    }
}
