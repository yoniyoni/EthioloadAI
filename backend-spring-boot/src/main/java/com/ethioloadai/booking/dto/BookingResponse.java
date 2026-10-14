package com.ethioloadai.booking.dto;

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
public class BookingResponse {

    private Long id;
    private Long freightId;
    private String freightDescription;
    private Long vehicleId;
    private String vehiclePlateNumber;
    private String vehicleType;
    private Long driverId;
    private String driverName;
    private Long tripId;
    private String tripStatus;
    private Long bidId;
    private String bookingStatus;
    private BigDecimal estimatedPrice;
    private BigDecimal commissionFee;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updatedAt;
}