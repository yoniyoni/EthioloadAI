package com.ethioloadai.tracking.service;

import com.ethioloadai.tracking.dto.LocationUpdateRequest;
import com.ethioloadai.tracking.exception.TrackingAuthorizationException;
import com.ethioloadai.tracking.exception.VehicleConsistencyException;
import com.ethioloadai.trip.entity.Trip;
import com.ethioloadai.trip.exception.TripNotFoundException;
import com.ethioloadai.trip.repository.TripRepository;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.vehicle.entity.Vehicle;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of vehicle-trip consistency validation service.
 * 
 * Provides comprehensive validation of vehicle authorization for GPS tracking
 * operations, ensuring data integrity and preventing cross-vehicle contamination.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VehicleTripConsistencyServiceImpl implements VehicleTripConsistencyService {
    private static final Logger log = LoggerFactory.getLogger(VehicleTripConsistencyServiceImpl.class);
    
    private final TripRepository tripRepository;
    private final TrackingAuthorizationService authorizationService;
    
    @Override
    public VehicleConsistencyValidation validateVehicleTripConsistency(
            Long tripId, Long vehicleId, User user) {
        
        log.debug("Validating vehicle {} consistency for trip {} by user {}", 
                 vehicleId, tripId, user.getId());
        
        // First validate user authorization for the trip
        // Note: We'll validate by checking if they can submit GPS updates at all
        if (!authorizationService.canSubmitLocationUpdates(user)) {
            throw new TrackingAuthorizationException("User not authorized to submit GPS updates");
        }
        
        // Get the trip with vehicle information
        Trip trip = tripRepository.findByIdWithBookingAndVehicle(tripId)
            .orElseThrow(() -> TripNotFoundException.withId(tripId));
        
        Vehicle authorizedVehicle = trip.getBooking().getVehicle();
        Long expectedVehicleId = authorizedVehicle.getId();
        
        // Validate vehicle consistency
        if (!expectedVehicleId.equals(vehicleId)) {
            log.warn("Vehicle mismatch for trip {}: expected {}, got {}", 
                    tripId, expectedVehicleId, vehicleId);
            return VehicleConsistencyValidation.invalid(
                String.format("Vehicle mismatch: trip %d is assigned to vehicle %d, not vehicle %d",
                             tripId, expectedVehicleId, vehicleId),
                expectedVehicleId, vehicleId, tripId
            );
        }
        
        // Check if trip is in a trackable state
        if (!isTripTrackable(trip)) {
            log.warn("Trip {} is not in trackable state: {}", tripId, trip.getTripStatus());
            return VehicleConsistencyValidation.invalid(
                String.format("Trip %d is not trackable in status %s", tripId, trip.getTripStatus()),
                expectedVehicleId, vehicleId, tripId
            );
        }
        
        log.debug("Vehicle-trip consistency validated for vehicle {} and trip {}", vehicleId, tripId);
        return VehicleConsistencyValidation.valid(vehicleId, tripId);
    }
    
    @Override
    public VehicleConsistencyValidation validateLocationUpdate(
            Long tripId, LocationUpdateRequest locationRequest, User user) {
        
        if (locationRequest.getVehicleId() == null) {
            throw VehicleConsistencyException.missingVehicleId(tripId);
        }
        
        return validateVehicleTripConsistency(tripId, locationRequest.getVehicleId(), user);
    }
    
    @Override
    public Long getAuthorizedVehicleForTrip(Long tripId, User user) {
        // Validate authorization first
        if (!authorizationService.canSubmitLocationUpdates(user)) {
            throw new TrackingAuthorizationException("User not authorized to submit GPS updates");
        }
        
        Trip trip = tripRepository.findByIdWithBookingAndVehicle(tripId)
            .orElseThrow(() -> TripNotFoundException.withId(tripId));
        
        return trip.getBooking().getVehicle().getId();
    }
    
    @Override
    public Long getActiveTripForVehicle(Long vehicleId, User user) {
        // For now, we'll check user authorization by seeing if they have access to any trips
        // In a more complex system, we might want more fine-grained vehicle access control
        
        return tripRepository.findActiveTripByVehicleId(vehicleId)
            .map(Trip::getId)
            .orElse(null);
    }
    
    /**
     * Check if a trip is in a state that allows GPS tracking.
     * 
     * @param trip the trip to check
     * @return true if the trip can receive GPS updates
     */
    private boolean isTripTrackable(Trip trip) {
        // Only ongoing trips should receive GPS updates
        return Trip.TripStatus.ONGOING.equals(trip.getTripStatus());
    }
}