package com.ethioloadai.carrier.fleet.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Represents a driver's membership in a carrier company.
 *
 * <p>Separates {@code User} identity from the driver's relationship with a
 * specific company. A driver (User with role DRIVER) may be associated with at
 * most one company at a time (enforced by the unique constraint).
 * Historical rows (INACTIVE / REMOVED) are preserved for trip traceability.
 *
 * <p>The driver's global {@link com.ethioloadai.user.entity.User} account is
 * never deleted when removing from a company.
 */
@Entity
@Table(
    name = "carrier_drivers",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_carrier_driver_active",
        columnNames = {"carrier_company_id", "driver_user_id"}
    )
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CarrierDriver {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "carrier_company_id", nullable = false)
    private Long carrierCompanyId;

    @Column(name = "driver_user_id", nullable = false)
    private Long driverUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private CarrierDriverStatus status = CarrierDriverStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private DriverAvailability availability = DriverAvailability.AVAILABLE;

    @Column(name = "added_by")
    private Long addedBy;

    @Column(name = "joined_at", nullable = false)
    @Builder.Default
    private LocalDateTime joinedAt = LocalDateTime.now();

    @Column(name = "left_at")
    private LocalDateTime leftAt;

    @Column(length = 1000)
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
