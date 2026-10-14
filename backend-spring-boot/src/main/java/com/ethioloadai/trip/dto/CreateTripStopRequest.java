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
public class CreateTripStopRequest {

    @NotBlank(message = "Location name is required")
    @Size(max = 255, message = "Location name must not exceed 255 characters")
    private String locationName;

    @Positive(message = "Stop order must be positive")
    private Integer stopOrder;

    @NotNull(message = "Pickup latitude is required")
    @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
    @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
    private BigDecimal pickupLatitude;

    @NotNull(message = "Pickup longitude is required")
    @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
    @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180")
    private BigDecimal pickupLongitude;

    @NotNull(message = "Agreed price is required")
    @DecimalMin(value = "0.01", message = "Agreed price must be greater than 0")
    @Digits(integer = 13, fraction = 2, message = "Price must have at most 13 integer digits and 2 decimal places")
    private BigDecimal agreedPrice;

    private Long cargoRequestId;
}