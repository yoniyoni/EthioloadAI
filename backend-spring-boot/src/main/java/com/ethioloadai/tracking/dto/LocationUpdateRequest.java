package com.ethioloadai.tracking.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Real-time GPS location update request for vehicle tracking.
 * Used for WebSocket and REST GPS updates.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocationUpdateRequest {
    
    /**
     * Vehicle transmitting the GPS location.
     * REQUIRED: Explicit vehicle identification prevents ambiguous selection.
     */
    @NotNull(message = "Vehicle ID is required for GPS tracking")
    @Positive(message = "Vehicle ID must be positive")
    private Long vehicleId;
    
    /**
     * GPS latitude in decimal degrees.
     * REQUIRED: Geographic position for tracking.
     * Precision: DECIMAL(10,8) allows ±90° with ~1mm accuracy.
     */
    @NotNull(message = "Latitude is required") 
    @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90 degrees")
    @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90 degrees")
    @Digits(integer = 2, fraction = 8, message = "Latitude precision: 2 integer, 8 decimal places")
    private BigDecimal latitude;
    
    /**
     * GPS longitude in decimal degrees.
     * REQUIRED: Geographic position for tracking.
     * Precision: DECIMAL(11,8) allows ±180° with ~1mm accuracy.
     */
    @NotNull(message = "Longitude is required")
    @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180 degrees") 
    @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180 degrees")
    @Digits(integer = 3, fraction = 8, message = "Longitude precision: 3 integer, 8 decimal places")
    private BigDecimal longitude;
    
    /**
     * GPS reading timestamp.
     * REQUIRED: Chronological ordering and out-of-order detection.
     */
    @NotNull(message = "Timestamp is required")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    private LocalDateTime timestamp;
    
    /**
     * Vehicle speed in km/h.
     * OPTIONAL: Used for route analytics and validation.
     */
    @DecimalMin(value = "0.0", message = "Speed cannot be negative")
    @DecimalMax(value = "200.0", message = "Speed cannot exceed 200 km/h") 
    @Digits(integer = 3, fraction = 2, message = "Speed precision: 3 integer, 2 decimal places")
    private BigDecimal speed;
    
    /**
     * GPS accuracy radius in meters.
     * OPTIONAL: Used for data quality assessment.
     */
    @DecimalMin(value = "0.0", message = "Accuracy cannot be negative")
    @DecimalMax(value = "1000.0", message = "Accuracy cannot exceed 1000 meters")
    @Digits(integer = 4, fraction = 2, message = "Accuracy precision: 4 integer, 2 decimal places")
    private BigDecimal accuracy;
    
    /**
     * Vehicle heading/direction in degrees (0-360).
     * OPTIONAL: Used for route analytics and direction tracking.
     */
    @DecimalMin(value = "0.0", message = "Heading must be between 0 and 360 degrees")
    @DecimalMax(value = "360.0", message = "Heading must be between 0 and 360 degrees")
    @Digits(integer = 3, fraction = 2, message = "Heading precision: 3 integer, 2 decimal places")  
    private BigDecimal heading;
}