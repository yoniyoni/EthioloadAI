package com.ethioloadai.freight.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateFreightRequest {
    @NotBlank(message = "Pickup location is required")
    @Size(max = 255, message = "Pickup location must not exceed 255 characters")
    private String pickupLocation;

    @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
    @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
    private BigDecimal pickupLatitude;

    @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
    @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180")
    private BigDecimal pickupLongitude;

    @NotBlank(message = "Destination is required")
    @Size(max = 255, message = "Destination must not exceed 255 characters")
    private String destination;

    @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
    @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
    private BigDecimal destinationLatitude;

    @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
    @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180")
    private BigDecimal destinationLongitude;

    @NotBlank(message = "Material type is required")
    @Pattern(regexp = "^(grain|cement|construction|perishables|electronics|livestock|fuel|general|other)$",
            message = "Material type must be one of: grain, cement, construction, perishables, electronics, livestock, fuel, general, other")
    private String materialType;

    private String cargoDescription;

    @NotNull(message = "Weight is required")
    @DecimalMin(value = "0.1", message = "Weight must be at least 0.1 tons")
    private BigDecimal weightTons;

    private BigDecimal volumeM3;

    @NotNull(message = "Budget is required")
    @DecimalMin(value = "1.0", message = "Budget must be at least 1 ETB")
    private BigDecimal budget;

    private BigDecimal distanceKm;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate deadline;

    @NotBlank(message = "Urgency level is required")
    @Pattern(regexp = "^(low|normal|high|urgent)$",
            message = "Urgency level must be one of: low, normal, high, urgent")
    private String urgencyLevel;
}
