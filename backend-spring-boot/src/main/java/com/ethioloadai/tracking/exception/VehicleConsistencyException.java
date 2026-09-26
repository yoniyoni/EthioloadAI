package com.ethioloadai.tracking.exception;

import com.ethioloadai.tracking.service.VehicleTripConsistencyService.VehicleConsistencyValidation;

/**
 * Exception thrown when vehicle-trip consistency validation fails.
 * 
 * This occurs when a GPS update is submitted for a trip by a vehicle
 * that is not authorized for that trip.
 */
public class VehicleConsistencyException extends RuntimeException {
    
    private final VehicleConsistencyValidation validation;
    
    public VehicleConsistencyException(VehicleConsistencyValidation validation) {
        super(validation.getReason());
        this.validation = validation;
    }
    
    public VehicleConsistencyException(String message, VehicleConsistencyValidation validation) {
        super(message);
        this.validation = validation;
    }
    
    public VehicleConsistencyValidation getValidation() {
        return validation;
    }
    
    public Long getExpectedVehicleId() {
        return validation.getExpectedVehicleId();
    }
    
    public Long getActualVehicleId() {
        return validation.getActualVehicleId();
    }
    
    public Long getTripId() {
        return validation.getTripId();
    }
    
    // Factory methods for common scenarios
    
    public static VehicleConsistencyException vehicleMismatch(Long tripId, Long expectedVehicleId, Long actualVehicleId) {
        VehicleConsistencyValidation validation = VehicleConsistencyValidation.invalid(
            String.format("Vehicle mismatch for trip %d: expected vehicle %d, got vehicle %d", 
                         tripId, expectedVehicleId, actualVehicleId),
            expectedVehicleId, actualVehicleId, tripId
        );
        return new VehicleConsistencyException(validation);
    }
    
    public static VehicleConsistencyException vehicleNotAssigned(Long vehicleId, Long tripId) {
        VehicleConsistencyValidation validation = VehicleConsistencyValidation.invalid(
            String.format("Vehicle %d is not assigned to trip %d", vehicleId, tripId),
            null, vehicleId, tripId
        );
        return new VehicleConsistencyException(validation);
    }
    
    public static VehicleConsistencyException vehicleAlreadyActive(Long vehicleId, Long activeTripId, Long requestedTripId) {
        VehicleConsistencyValidation validation = VehicleConsistencyValidation.invalid(
            String.format("Vehicle %d is already active on trip %d, cannot update trip %d", 
                         vehicleId, activeTripId, requestedTripId),
            activeTripId, vehicleId, requestedTripId
        );
        return new VehicleConsistencyException(validation);
    }
    
    public static VehicleConsistencyException missingVehicleId(Long tripId) {
        VehicleConsistencyValidation validation = VehicleConsistencyValidation.invalid(
            String.format("GPS update for trip %d missing required vehicleId field", tripId),
            null, null, tripId
        );
        return new VehicleConsistencyException(validation);
    }
}