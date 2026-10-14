package com.ethioloadai.trip.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddGpsLocationRequest {

    @NotNull(message = "Latitude is required")
    @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
    @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
    @Digits(integer = 2, fraction = 7, message = "Latitude must have at most 2 integer digits and 7 decimal places")
    private Double latitude;

    @NotNull(message = "Longitude is required") 
    @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
    @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180")
    @Digits(integer = 3, fraction = 7, message = "Longitude must have at most 3 integer digits and 7 decimal places")
    private Double longitude;

    @DecimalMin(value = "0.0", message = "Speed must be non-negative")
    @DecimalMax(value = "200.0", message = "Speed must not exceed 200 km/h")
    private Double speed;

    @DecimalMin(value = "0.0", message = "Accuracy must be non-negative")
    @DecimalMax(value = "1000.0", message = "Accuracy must not exceed 1000 meters")
    private Double accuracy;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;
}