package com.ethioloadai.trip.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TripResponse {

    private Long id;
    
    // Basic trip information
    private String startLocation;
    private String destination;
    private String tripStatus;
    private String tripType;
    private Integer totalStops;
    private Integer completedStops;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime startTime;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime endTime;
    
    // Related entity information
    private BookingInfo booking;
    private DriverInfo driver;
    private ShipperInfo shipper;
    private VehicleInfo vehicle;
    private CargoInfo cargo;
    
    // Route and progress information
    private RouteInfo routeInfo;
    private List<TripStopResponse> stops;
    
    // Timestamps
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updatedAt;

    // Nested DTOs for related information
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class BookingInfo {
        private Long id;
        private String status;
        private BigDecimal estimatedPrice;
        private BigDecimal commissionFee;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DriverInfo {
        private Long id;
        private String fullName;
        private String phone;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ShipperInfo {
        private Long id;
        private String fullName;
        private String phone;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class VehicleInfo {
        private Long id;
        private String plateNumber;
        private String truckType;
        private BigDecimal loadCapacity;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CargoInfo {
        private Long id;
        private String materialType;
        private BigDecimal weightTons;
        private BigDecimal volumeM3;
        private String urgencyLevel;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RouteInfo {
        private BigDecimal totalDistanceKm;
        private BigDecimal completionPercentage;
        private Integer gpsPointCount;
        private String lastGpsUpdate;
        private BigDecimal averageSpeed;
    }
}