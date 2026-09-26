package com.ethioloadai.fleet.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AdminFleetOwnerResponse {

    private Long id;
    private String name;
    private String email;
    private String phone;
    private String role;

    @JsonProperty("isVerified")
    private Boolean isVerified;

    @JsonProperty("isActive")
    private Boolean isActive;

    private Integer driverCount;
    private Integer vehicleCount;

    private List<AdminFleetDriverSummary> drivers;
    private List<AdminFleetVehicleSummary> vehicles;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updatedAt;

    // ── Snake_case getters for legacy frontend compatibility ──────────────────

    @JsonProperty("driver_count")
    public Integer getDriverCountSnake() {
        return driverCount;
    }

    @JsonProperty("vehicle_count")
    public Integer getVehicleCountSnake() {
        return vehicleCount;
    }

    @JsonProperty("is_verified")
    public Boolean getIsVerifiedSnake() {
        return isVerified;
    }

    @JsonProperty("is_active")
    public Boolean getIsActiveSnake() {
        return isActive;
    }

    @JsonProperty("created_at")
    public LocalDateTime getCreatedAtSnake() {
        return createdAt;
    }

    @JsonProperty("updated_at")
    public LocalDateTime getUpdatedAtSnake() {
        return updatedAt;
    }
}
