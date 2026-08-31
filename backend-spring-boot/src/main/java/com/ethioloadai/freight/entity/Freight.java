package com.ethioloadai.freight.entity;

import com.ethioloadai.user.entity.User;
import com.ethioloadai.vehicle.entity.Vehicle;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "freight")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Freight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "shipper_id", nullable = false)
    private User shipper;

    @Column(name = "pickup_location", nullable = false, length = 255)
    private String pickupLocation;

    @Column(name = "pickup_latitude", precision = 10, scale = 7)
    private BigDecimal pickupLatitude;

    @Column(name = "pickup_longitude", precision = 10, scale = 7)
    private BigDecimal pickupLongitude;

    @Column(name = "destination", nullable = false, length = 255)
    private String destination;

    @Column(name = "destination_latitude", precision = 10, scale = 7)
    private BigDecimal destinationLatitude;

    @Column(name = "destination_longitude", precision = 10, scale = 7)
    private BigDecimal destinationLongitude;

    @Column(name = "material_type", nullable = false, length = 50)
    private String materialType;

    @Column(name = "cargo_description", columnDefinition = "TEXT")
    private String cargoDescription;

    @Column(name = "weight_tons", nullable = false, precision = 10, scale = 2)
    private BigDecimal weightTons;

    @Column(name = "volume_m3", precision = 10, scale = 2)
    private BigDecimal volumeM3;

    @Column(name = "budget", nullable = false, precision = 15, scale = 2)
    private BigDecimal budget;

    @Column(name = "distance_km", precision = 10, scale = 2)
    private BigDecimal distanceKm;

    @Column(name = "deadline")
    private LocalDate deadline;

    @Column(name = "urgency_level", nullable = false, length = 20)
    private String urgencyLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private FreightStatus status = FreightStatus.POSTED;

    @ManyToOne
    @JoinColumn(name = "matched_driver_id")
    private User matchedDriver;

    @ManyToOne
    @JoinColumn(name = "matched_vehicle_id")
    private Vehicle matchedVehicle;

    @Column(name = "matched_price", precision = 15, scale = 2)
    private BigDecimal matchedPrice;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public enum FreightStatus {
        POSTED,
        MATCHED,
        IN_TRANSIT,
        DELIVERED,
        COMPLETED,
        CANCELLED
    }
}
