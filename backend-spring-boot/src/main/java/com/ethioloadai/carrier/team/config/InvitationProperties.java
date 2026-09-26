package com.ethioloadai.carrier.team.config;
import lombok.*;
import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
@Component @ConfigurationProperties(prefix="carrier.invitations") @Validated
@Getter @Setter
public class InvitationProperties {
 @Min(1) @Max(168) private int expiryHours = 24;
 @Min(0) private int cooldownSeconds = 60;
 @Min(1) @Max(100) private int maxPerHour = 20;
 private String frontendBaseUrl;
 private String emailFrom;
}

