package com.ethioloadai.freight.dto;

import com.ethioloadai.freight.entity.Freight;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateFreightRequest {
    
    // ── Core freight fields (existing) ──────────────────────────────────────
    
    @Size(max = 255, message = "Pickup location must not exceed 255 characters")
    private String pickupLocation;

    @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
    @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
    private BigDecimal pickupLatitude;

    @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
    @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180")
    private BigDecimal pickupLongitude;

    @Size(max = 255, message = "Destination must not exceed 255 characters")
    private String destination;

    @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
    @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
    private BigDecimal destinationLatitude;

    @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
    @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180")
    private BigDecimal destinationLongitude;

    @Pattern(regexp = "^(grain|cement|construction|perishables|electronics|livestock|fuel|general|other)$",
            message = "Material type must be one of: grain, cement, construction, perishables, electronics, livestock, fuel, general, other")
    private String materialType;

    private String cargoDescription;

    @DecimalMin(value = "0.1", message = "Weight must be at least 0.1 tons")
    private BigDecimal weightTons;

    private BigDecimal volumeM3;

    @DecimalMin(value = "1.0", message = "Budget must be at least 1 ETB when specified")
    private BigDecimal budget;

    private BigDecimal distanceKm;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate deadline;

    @Pattern(regexp = "^(low|normal|high|urgent)$",
            message = "Urgency level must be one of: low, normal, high, urgent")
    private String urgencyLevel;

    // ── V6 fields: bidding and intracity support ──────────────────────────────

    /** Service type: intercity (default) or intracity */
    @Builder.Default
    private Freight.ServiceType serviceType = Freight.ServiceType.INTERCITY;

    /** Price type: negotiable (drivers bid) or fixed (drivers accept set price) */
    @Builder.Default
    private Freight.PriceType priceType = Freight.PriceType.NEGOTIABLE;

    /** Deadline after which no new bids are accepted (for negotiable pricing) */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime bidDeadline;

    /** City name for intracity requests */
    @Size(max = 255, message = "City name must not exceed 255 characters")
    private String city;

    /** Pickup area/neighbourhood within the city (intracity) */
    @Size(max = 255, message = "Pickup area must not exceed 255 characters")
    private String pickupArea;

    /** Drop-off area/neighbourhood within the city (intracity) */
    @Size(max = 255, message = "Dropoff area must not exceed 255 characters")
    private String dropoffArea;

    /** Preferred pickup/delivery date */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate preferredDate;

    /** Free-text description of items for intracity/general cargo */
    @Size(max = 2000, message = "Items description must not exceed 2000 characters")
    private String itemsDescription;

    /** Required vehicle type (e.g. 'pickup', 'box truck') */
    @Size(max = 255, message = "Vehicle type needed must not exceed 255 characters")
    private String vehicleTypeNeeded;
}
