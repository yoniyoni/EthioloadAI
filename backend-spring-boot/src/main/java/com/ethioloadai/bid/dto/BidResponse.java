package com.ethioloadai.bid.dto;

import com.ethioloadai.bid.entity.Bid;
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
public class BidResponse {

    private Long id;
    private Long freightId;
    private Long driverId;
    private String driverName;
    private Long vehicleId;
    private String vehiclePlateNumber;
    private String vehicleType;
    private BigDecimal amount;
    private String note;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime availableDatetime;
    
    private String status;
    private BigDecimal aiScore;
    private Boolean isRecommended;
    private BigDecimal distanceKm;
    
    // Counter-offer fields
    private BigDecimal counterAmount;
    private String counterNote;
    private String counterBy;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime counterAt;

    // Cargo summary fields (for driver bid cards)
    private String pickupLocation;
    private String destination;
    private String materialType;
    private BigDecimal weightTons;
    private BigDecimal budget;
    private String shipperName;
    private String shipperPhone;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updatedAt;
}