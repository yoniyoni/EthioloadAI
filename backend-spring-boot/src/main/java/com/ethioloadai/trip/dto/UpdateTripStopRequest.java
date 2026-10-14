package com.ethioloadai.trip.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTripStopRequest {

    @Size(max = 255, message = "Location name must not exceed 255 characters")
    private String locationName;

    @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
    @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
    private BigDecimal pickupLatitude;

    @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
    @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180")
    private BigDecimal pickupLongitude;

    @DecimalMin(value = "0.01", message = "Agreed price must be greater than 0")
    @Digits(integer = 13, fraction = 2, message = "Price must have at most 13 integer digits and 2 decimal places")
    private BigDecimal agreedPrice;
}