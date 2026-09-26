package com.ethioloadai.trip.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TripStopResponse {

    private Long id;
    private Long tripId;
    
    // Location information
    private String locationName;
    private BigDecimal pickupLatitude;
    private BigDecimal pickupLongitude;
    
    // Stop details
    private Integer stopOrder;
    private String stopStatus;
    private BigDecimal agreedPrice;
    
    // Cargo information (if applicable)
    private CargoStopInfo cargoInfo;
    
    // Timing information
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime arrivalTime;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime loadingTime;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime completionTime;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CargoStopInfo {
        private Long cargoRequestId;
        private String materialType;
        private BigDecimal weightTons;
        private String shipperName;
    }
}