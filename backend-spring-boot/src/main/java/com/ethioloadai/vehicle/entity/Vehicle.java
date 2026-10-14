package com.ethioloadai.vehicle.entity;

import com.ethioloadai.user.entity.User;
import com.ethioloadai.vehicle.converter.VehicleCategoryConverter;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "vehicles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Vehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "fleet_owner_id")
    private Long fleetOwnerId;

    /**
     * Phase 4 — the carrier company this vehicle belongs to.
     * Nullable: vehicles registered before Phase 4 or by independent fleet owners
     * do not yet have a carrier company assignment.
     */
    @Column(name = "carrier_company_id")
    private Long carrierCompanyId;

    @Column(name = "truck_type", nullable = false, length = 255)
    private String truckType;

    // Persisted as lowercase ('heavy'/'light') to match the PostgreSQL CHECK constraint.
    // Uses VehicleCategoryConverter — @Enumerated(EnumType.STRING) would write uppercase.
    @Convert(converter = VehicleCategoryConverter.class)
    @Column(name = "vehicle_category", length = 50)
    private VehicleCategory vehicleCategory;

    @Column(name = "plate_number", unique = true, nullable = false, length = 255)
    private String plateNumber;

    @Column(name = "capacity", nullable = false)
    private Double capacity;

    @Column(name = "current_city", length = 255)
    private String currentCity;

    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "last_location_at")
    private LocalDateTime lastLocationAt;

    @Column(name = "availability_status", nullable = false, length = 50)
    @Builder.Default
    private String availabilityStatus = "available";

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", nullable = false, length = 20)
    @Builder.Default
    private VehicleApprovalStatus approvalStatus = VehicleApprovalStatus.UNDER_REVIEW;

    @Column(name = "rating", precision = 3, scale = 2)
    private BigDecimal rating;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public enum VehicleCategory {
        HEAVY,
        LIGHT
    }

    public enum VehicleApprovalStatus {
        UNDER_REVIEW,
        APPROVED,
        REJECTED,
        SUSPENDED
    }

    // ── Explicit accessors (supplement Lombok @Data) ──────────────────────────
    public Long getId()              { return id; }
    public Long getFleetOwnerId()    { return fleetOwnerId; }
}
