package com.ethioloadai.carrier.fleet.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Records a driver ↔ vehicle assignment within a carrier company.
 *
 * <p>Every assignment event is persisted. Only one ACTIVE assignment per
 * driver and per vehicle is allowed at any time (enforced by the partial
 * unique indexes {@code uq_active_driver_assignment} and
 * {@code uq_active_vehicle_assignment} in V63).
 *
 * <p>To reassign: end the current assignment, then create a new one.
 * History is never deleted.
 */
@Entity
@Table(name = "vehicle_driver_assignments")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class VehicleDriverAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "carrier_company_id", nullable = false)
    private Long carrierCompanyId;

    /** FK to carrier_drivers.id — the company-driver relationship row. */
    @Column(name = "carrier_driver_id", nullable = false)
    private Long carrierDriverId;

    @Column(name = "vehicle_id", nullable = false)
    private Long vehicleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private AssignmentStatus status = AssignmentStatus.ACTIVE;

    @Column(name = "assigned_by", nullable = false)
    private Long assignedBy;

    @Column(name = "ended_by")
    private Long endedBy;

    @Column(name = "assigned_at", nullable = false)
    @Builder.Default
    private LocalDateTime assignedAt = LocalDateTime.now();

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(length = 1000)
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
