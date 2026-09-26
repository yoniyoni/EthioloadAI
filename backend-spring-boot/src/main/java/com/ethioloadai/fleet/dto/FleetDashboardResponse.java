package com.ethioloadai.fleet.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Fleet dashboard summary for fleet owners.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FleetDashboardResponse {
    private Long fleetOwnerId;
    private int driverCount;
    private int vehicleCount;
    private int activeBookingCount;
    private List<DriverSummary> drivers;
    private List<VehicleSummary> vehicles;
    private List<FleetBookingSummary> activeBookings;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DriverSummary {
        private Long id;
        private String fullName;
        private String phone;
        private Boolean isActive;
        private Boolean verificationStatus;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VehicleSummary {
        private Long id;
        private String plateNumber;
        private String truckType;
        private Double capacity;
        private String availabilityStatus;
        private Long assignedDriverId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FleetBookingSummary {
        private Long bookingId;
        private Long driverId;
        private String driverName;
        private String status;
        private String route;
    }
}
