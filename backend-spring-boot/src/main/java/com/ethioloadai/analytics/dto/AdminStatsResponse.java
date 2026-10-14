package com.ethioloadai.analytics.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AdminStatsResponse {
    private Long totalUsers;
    private Long totalDrivers;
    private Long verifiedDrivers;
    private Long activeFreight;
    private Long completedDeliveries;
    private Double totalRevenue;
    private Double escrowHeld;
    private Double totalCommissions;
    private Long pendingVerifications;
    private Double averageDeliveryTime;
    private Long activeVehicles;
    private Integer successRate;
    private Long openDisputes;

    private Map<String, Object> users;
    private Map<String, Object> drivers;
    private Map<String, Object> freight;
    private Map<String, Object> payments;
}
