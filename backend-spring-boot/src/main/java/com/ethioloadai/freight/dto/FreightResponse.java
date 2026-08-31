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
}
