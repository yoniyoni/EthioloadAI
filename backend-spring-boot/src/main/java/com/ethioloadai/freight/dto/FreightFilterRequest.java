package com.ethioloadai.freight.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FreightFilterRequest {
    
    // ── Core filter fields ─────────────────────────────────────────────────
    
    private String status;
    private String materialType;
    private String pickupLocation;
    private String destination;
    private BigDecimal minWeight;
    private BigDecimal maxWeight;
    private BigDecimal minBudget;
    private BigDecimal maxBudget;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate deadlineAfter;
    private Integer page;
    private Integer size;

    // ── V6 filter fields ───────────────────────────────────────────────────

    /** Filter by service type: intercity or intracity */
    private String serviceType;

    /** Filter by price type: negotiable or fixed */
    private String priceType;

    /** Filter by city for intracity freight */
    private String city;

    /** Filter by pickup area within city */
    private String pickupArea;

    /** Filter by dropoff area within city */
    private String dropoffArea;

    /** Filter by preferred date (after this date) */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate preferredDateAfter;

    /** Filter by vehicle type needed */
    private String vehicleTypeNeeded;

    /** Filter freight with bids still open (bidDeadline after now) */
    private Boolean bidsOpen;
}
