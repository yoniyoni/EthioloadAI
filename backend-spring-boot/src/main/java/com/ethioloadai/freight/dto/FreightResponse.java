package com.ethioloadai.freight.dto;

import com.ethioloadai.freight.entity.Freight;
import com.fasterxml.jackson.annotation.JsonFormat;
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
public class FreightResponse {
    
    // ── Core freight fields ────────────────────────────────────────────────
    
    private Long id;
    private Long shipperId;
    private String shipperName;
    private String pickupLocation;
    private BigDecimal pickupLatitude;
    private BigDecimal pickupLongitude;
    private String destination;
    private BigDecimal destinationLatitude;
    private BigDecimal destinationLongitude;
    private String materialType;
    private String cargoDescription;
    private BigDecimal weightTons;
    private BigDecimal volumeM3;
    private BigDecimal budget;
    private BigDecimal distanceKm;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate deadline;
    private String urgencyLevel;
    private String status;
    private Long matchedDriverId;
    private String matchedDriverName;
    private Long matchedVehicleId;
    private BigDecimal matchedPrice;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updatedAt;

    // ── V6 fields: bidding and intracity support ──────────────────────────────

    /** Service type: intercity or intracity */
    private String serviceType;

    /** Price type: negotiable or fixed */
    private String priceType;

    /** Deadline after which no new bids are accepted */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime bidDeadline;

    /** City name for intracity requests */
    private String city;

    /** Pickup area/neighbourhood within the city (intracity) */
    private String pickupArea;

    /** Drop-off area/neighbourhood within the city (intracity) */
    private String dropoffArea;

    /** Preferred pickup/delivery date */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate preferredDate;

    /** Free-text description of items for intracity/general cargo */
    private String itemsDescription;

    /** Required vehicle type (e.g. 'pickup', 'box truck') */
    private String vehicleTypeNeeded;
}
