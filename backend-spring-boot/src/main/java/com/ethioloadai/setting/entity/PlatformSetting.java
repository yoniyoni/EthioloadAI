package com.ethioloadai.setting.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Admin-configurable key-value settings for the platform.
 *
 * <p>Known keys (seeded in V15 migration):
 * <ul>
 *   <li>{@code pricing.rate_min}              — ETB/km/ton minimum rate (default: 18)</li>
 *   <li>{@code pricing.rate_max}              — ETB/km/ton maximum rate (default: 28)</li>
 *   <li>{@code pricing.intracity_rate_min}    — intracity ETB/km minimum (default: 50)</li>
 *   <li>{@code pricing.intracity_rate_max}    — intracity ETB/km maximum (default: 100)</li>
 *   <li>{@code pricing.commission_rate}       — platform commission (default: 0.10)</li>
 *   <li>{@code pricing.fleet_commission_rate} — fleet commission (default: 0.05)</li>
 * </ul>
 *
 * <p>Values are stored as TEXT and parsed by the service layer as needed.
 */
@Entity
@Table(name = "platform_settings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "setting_key", nullable = false, unique = true, length = 255)
    private String key;

    @Column(name = "value", columnDefinition = "TEXT")
    private String value;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
