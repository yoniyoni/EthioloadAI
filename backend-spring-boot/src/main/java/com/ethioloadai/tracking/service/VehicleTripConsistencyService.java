package com.ethioloadai.tracking.service;

import com.ethioloadai.tracking.dto.LocationUpdateRequest;
import com.ethioloadai.trip.entity.Trip;
import com.ethioloadai.user.entity.User;

/**
 * Service for validating vehicle-trip consistency in GPS tracking operations.
 * 
 * Ensures that GPS location updates are submitted by the correct vehicle
 * for the specified trip, preventing cross-vehicle data contamination.
 */
public interface VehicleTripConsistencyService {
    
    /**
     * Validate that the specified vehicle is authorized to submit GPS updates for a trip.
     * 
     * @param tripId the trip receiving GPS updates
     * @param vehicleId the vehicle submitting GPS data
     * @param user the user submitting the update (for authorization)
     * @return validation result with details
     * @throws VehicleConsistencyException if validation fails
     */
    VehicleConsistencyValidation validateVehicleTripConsistency(
            Long tripId, Long vehicleId, User user);
    
    /**
     * Validate location update request for vehicle-trip consistency.
     * 
     * @param tripId the trip receiving GPS updates
     * @param locationRequest the GPS location update request
     * @param user the user submitting the update
     * @return validation result with details
     * @throws VehicleConsistencyException if validation fails
     */
    VehicleConsistencyValidation validateLocationUpdate(
            Long tripId, LocationUpdateRequest locationRequest, User user);
    
    /**
     * Get the authorized vehicle ID for a trip.
     * 
     * @param tripId the trip ID
     * @param user the requesting user
     * @return the vehicle ID authorized for this trip
     * @throws TripNotFoundException if trip not found
     * @throws TrackingAuthorizationException if user not authorized
     */
    Long getAuthorizedVehicleForTrip(Long tripId, User user);
    
    /**
     * Check if a vehicle is currently assigned to any active trip.
     * 
     * @param vehicleId the vehicle ID to check
     * @param user the requesting user
     * @return active trip ID if vehicle is assigned, null otherwise
     */
    Long getActiveTripForVehicle(Long vehicleId, User user);
    
    /**
     * Validation result for vehicle-trip consistency checks.
     */
    public static class VehicleConsistencyValidation {
        private final boolean valid;
        private final String reason;
        private final Long expectedVehicleId;
        private final Long actualVehicleId;
        private final Long tripId;
        
        public VehicleConsistencyValidation(boolean valid, String reason, 
                                          Long expectedVehicleId, Long actualVehicleId, Long tripId) {
            this.valid = valid;
            this.reason = reason;
            this.expectedVehicleId = expectedVehicleId;
            this.actualVehicleId = actualVehicleId;
            this.tripId = tripId;
        }
        
        public boolean isValid() { return valid; }
        public String getReason() { return reason; }
        public Long getExpectedVehicleId() { return expectedVehicleId; }
        public Long getActualVehicleId() { return actualVehicleId; }
        public Long getTripId() { return tripId; }
        
        public static VehicleConsistencyValidation valid(Long vehicleId, Long tripId) {
            return new VehicleConsistencyValidation(true, "Vehicle-trip consistency validated", 
                                                   vehicleId, vehicleId, tripId);
        }
        
        public static VehicleConsistencyValidation invalid(String reason, 
                                                         Long expectedVehicleId, Long actualVehicleId, Long tripId) {
            return new VehicleConsistencyValidation(false, reason, 
                                                   expectedVehicleId, actualVehicleId, tripId);
        }
    }
}