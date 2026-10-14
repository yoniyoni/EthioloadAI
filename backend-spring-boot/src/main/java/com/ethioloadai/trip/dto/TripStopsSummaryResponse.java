package com.ethioloadai.trip.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TripStopsSummaryResponse {

    private Long tripId;
    
    // Stop counts
    private Integer totalStops;
    private Integer completedStops;
    private Integer pendingStops;
    private Integer inProgressStops;
    
    // Financial summary
    private BigDecimal totalAmount;
    
    // Progress metrics
    private Double completionPercentage;
    
    // Optional additional metrics
    private Integer stopsRequiringAttention;
    private BigDecimal completedAmount;
    private BigDecimal pendingAmount;
}