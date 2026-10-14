package com.ethioloadai.freight.entity;

import com.ethioloadai.freight.converter.FreightPriceTypeConverter;
import com.ethioloadai.freight.converter.FreightServiceTypeConverter;
import com.ethioloadai.freight.converter.FreightStatusConverter;
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

    // ── V6 fields: bidding and intracity support ──────────────────────────────

    /** intercity (default) or intracity. Affects bidding rules and pricing. */
    @Convert(converter = FreightServiceTypeConverter.class)
    @Column(name = "service_type", nullable = false, length = 20)
    @Builder.Default
    private ServiceType serviceType = ServiceType.INTERCITY;

    /** negotiable (drivers bid) or fixed (drivers accept a set price). */
    @Convert(converter = FreightPriceTypeConverter.class)
    @Column(name = "price_type", nullable = false, length = 20)
    @Builder.Default
    private PriceType priceType = PriceType.NEGOTIABLE;

    /** Deadline after which no new bids are accepted. */
    @Column(name = "bid_deadline")
    private LocalDateTime bidDeadline;

    /** City name for intracity requests. */
    @Column(name = "city", length = 255)
    private String city;

    /** Pickup area/neighbourhood within the city (intracity). */
    @Column(name = "pickup_area", length = 255)
    private String pickupArea;

    /** Drop-off area/neighbourhood within the city (intracity). */
    @Column(name = "dropoff_area", length = 255)
    private String dropoffArea;

    /** Preferred pickup/delivery date. */
    @Column(name = "preferred_date")
    private LocalDate preferredDate;

    /** Free-text description of items for intracity/general cargo. */
    @Column(name = "items_description", columnDefinition = "TEXT")
    private String itemsDescription;

    /** Required vehicle type (e.g. 'pickup', 'box truck'). */
    @Column(name = "vehicle_type_needed", length = 255)
    private String vehicleTypeNeeded;

    // Persisted as lowercase ('posted', 'in_transit', …) to match the PostgreSQL CHECK constraint.
    // Uses FreightStatusConverter — @Enumerated(EnumType.STRING) would write uppercase.
    @Convert(converter = FreightStatusConverter.class)
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

    /**
     * Phase 5: The accepted carrier offer that has won the commercial award.
     * Nullable — only set when a CarrierOffer is accepted for this freight.
     * Phase 6 will use this to start dispatch (driver + vehicle assignment).
     */
    @Column(name = "carrier_award_offer_id")
    private Long carrierAwardOfferId;

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

    public enum ServiceType {
        INTERCITY,
        INTRACITY
    }

    public enum PriceType {
        NEGOTIABLE,
        FIXED
    }
}
