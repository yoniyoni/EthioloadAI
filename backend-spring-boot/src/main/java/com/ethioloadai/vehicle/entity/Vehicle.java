package com.ethioloadai.vehicle.entity;

import com.ethioloadai.user.entity.User;
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

    @Column(name = "truck_type", nullable = false, length = 255)
    private String truckType;

    @Enumerated(EnumType.STRING)
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
}
