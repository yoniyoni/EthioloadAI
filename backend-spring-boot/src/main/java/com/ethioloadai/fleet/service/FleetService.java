package com.ethioloadai.fleet.service;

import com.ethioloadai.fleet.dto.*;
import com.ethioloadai.user.entity.User;

import java.util.List;

/**
 * Fleet management service - thin orchestration layer over existing User/Vehicle/Booking services.
 * Provides fleet owner-specific operations without duplicating existing domain logic.
 */
public interface FleetService {

    /**
     * Get fleet owner dashboard summary (drivers, vehicles, active bookings).
     *
     * @param fleetOwnerId the fleet owner user ID
     * @return dashboard data
     */
    FleetDashboardResponse getDashboard(Long fleetOwnerId);

    /**
     * List all drivers in this fleet.
     *
     * @param fleetOwnerId the fleet owner user ID
     * @return list of fleet drivers
     */
    List<FleetDriverResponse> getFleetDrivers(Long fleetOwnerId);

    /**
     * List all vehicles in this fleet.
     *
     * @param fleetOwnerId the fleet owner user ID
     * @return list of fleet vehicles
     */
    List<FleetVehicleResponse> getFleetVehicles(Long fleetOwnerId);

    /**
     * Add a driver to the fleet. The driver must already exist and have role DRIVER.
     * Server validates that the driver is not already in another fleet.
     *
     * @param fleetOwnerId the fleet owner user ID
     * @param request contains driver ID
     * @return added driver details
     */
    FleetDriverResponse addDriver(Long fleetOwnerId, AddDriverRequest request);

    /**
     * Remove a driver from the fleet. Sets driver.fleetOwnerId = null.
     * Server validates ownership before removal.
     *
     * @param fleetOwnerId the fleet owner user ID
     * @param driverId the driver to remove
     */
    void removeDriver(Long fleetOwnerId, Long driverId);

    /**
     * Register a new vehicle directly under this fleet owner.
     * Creates vehicle with fleetOwnerId = fleetOwnerId.
     *
     * @param fleetOwnerId the fleet owner user ID
     * @param request vehicle details
     * @return created vehicle
     */
    FleetVehicleResponse addVehicle(Long fleetOwnerId, AddVehicleRequest request);

    /**
     * Assign a fleet vehicle to a fleet driver.
     * Server validates that both vehicle and driver belong to this fleet owner.
     *
     * @param fleetOwnerId the fleet owner user ID
     * @param vehicleId the vehicle to assign
     * @param request contains driver ID
     * @return updated vehicle
     */
    FleetVehicleResponse assignVehicle(Long fleetOwnerId, Long vehicleId, AssignVehicleRequest request);

    /**
     * Verify that the given user is a fleet owner (role FLEET_OWNER).
     *
     * @param userId user to validate
     * @return the User entity
     * @throws com.ethioloadai.fleet.exception.FleetException.FleetAuthorizationException if not a fleet owner
     */
    User validateFleetOwner(Long userId);

    /**
     * Admin: retrieve all fleet owners with their associated drivers and vehicles.
     * Uses batch queries to avoid N+1 queries.
     *
     * @param adminUser authenticated administrator
     * @return list of fleet owner responses with driver/vehicle summaries
     */
    List<AdminFleetOwnerResponse> getAllFleetOwners(User adminUser);
}
