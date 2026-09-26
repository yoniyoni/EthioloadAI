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
public class UpdateFreightRequest {
    
    // ── Core updatable fields ──────────────────────────────────────────────
    
    private String cargoDescription;

    @DecimalMin(value = "1.0", message = "Budget must be at least 1 ETB")
    private BigDecimal budget;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate deadline;

    @Pattern(regexp = "^(low|normal|high|urgent)$",
            message = "Urgency level must be one of: low, normal, high, urgent")
    private String urgencyLevel;

    private Freight.FreightStatus status;

    // ── V6 updatable fields ────────────────────────────────────────────────

    /** Price type: negotiable or fixed (can be changed while POSTED) */
    private Freight.PriceType priceType;

    /** Deadline after which no new bids are accepted */
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
