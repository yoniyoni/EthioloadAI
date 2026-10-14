package com.ethioloadai.tracking.service;

import com.ethioloadai.tracking.dto.LocationUpdateRequest;
import com.ethioloadai.tracking.exception.TrackingAuthorizationException;
import com.ethioloadai.trip.entity.Trip;
import com.ethioloadai.trip.repository.TripRepository;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.vehicle.entity.Vehicle;
import com.ethioloadai.vehicle.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Comprehensive authorization service for GPS tracking operations.
 * Implements multi-layer security validation to prevent IDOR attacks.
 */
@Service
@RequiredArgsConstructor
public class TrackingAuthorizationServiceImpl implements TrackingAuthorizationService {
    private static final Logger log = LoggerFactory.getLogger(TrackingAuthorizationServiceImpl.class);

    private final VehicleRepository vehicleRepository;
    private final TripRepository tripRepository;

    @Override
    @Transactional(readOnly = true)
    public TrackingAuthorizationResult validateLocationUpdateAuthorization(LocationUpdateRequest request, User user) {
        log.debug("Validating location update authorization for user {} vehicle {} trip {}", 
                 user.getId(), request.getVehicleId(), request.getVehicleId());

        // 1. Validate user can submit tracking data
        if (!canSubmitLocationUpdates(user)) {
            throw TrackingAuthorizationException.userCannotSubmitTracking(user.getId(), user.getRole().name());
        }

        // 2. Validate vehicle exists and user has access
        Vehicle vehicle = vehicleRepository.findById(request.getVehicleId())
                .orElseThrow(() -> TrackingAuthorizationException.vehicleNotFound(request.getVehicleId()));

        if (!hasVehicleAccess(vehicle, user)) {
            throw TrackingAuthorizationException.unauthorizedVehicleAccess(request.getVehicleId(), user.getId());
        }

        // 3. Find trip associated with this vehicle and user
        Trip trip = findAuthorizedTrip(vehicle, user);

        // 4. Validate trip-vehicle consistency
        if (!isTripVehicleConsistent(trip, vehicle, user)) {
            throw TrackingAuthorizationException.tripVehicleMismatch(
                trip.getId(), request.getVehicleId(), trip.getBooking().getVehicle().getId());
        }

        // 5. Validate trip is trackable
        if (!isTripTrackable(trip)) {
            throw TrackingAuthorizationException.tripNotTrackable(trip.getId(), trip.getTripStatus().name());
        }

        // Determine authorization level
        AuthorizationLevel level = determineAuthorizationLevel(vehicle, user);

        log.debug("Location update authorization granted for user {} vehicle {} trip {} with level {}", 
                 user.getId(), vehicle.getId(), trip.getId(), level);

        return TrackingAuthorizationResult.authorized(vehicle, trip, level);
    }

    @Override
    public boolean canSubmitLocationUpdates(User user) {
        if (user == null || user.getRole() == null) {
            return false;
        }

        // Only drivers, fleet owners, and admins can submit GPS data
        return switch (user.getRole()) {
            case DRIVER, FLEET_OWNER, ADMIN -> true;
            case SHIPPER -> false; // Shippers cannot submit GPS updates
        };
    }

    @Override
    public boolean hasVehicleAccess(Vehicle vehicle, User user) {
        if (vehicle == null || user == null) {
            return false;
        }

        return switch (user.getRole()) {
            case ADMIN -> true; // Admins can access any vehicle

            case DRIVER -> {
                // Driver must own vehicle OR be authorized for fleet vehicle
                if (vehicle.getUserId().equals(user.getId())) {
                    yield true; // Driver owns the vehicle
                }
                
                // Check if driver is authorized for fleet vehicle
                if (vehicle.getFleetOwnerId() != null && user.getFleetOwnerId() != null) {
                    yield vehicle.getFleetOwnerId().equals(user.getFleetOwnerId());
                }
                
                yield false;
            }

            case FLEET_OWNER -> {
                // Fleet owner must own the fleet that owns the vehicle
                yield vehicle.getFleetOwnerId() != null && 
                      vehicle.getFleetOwnerId().equals(user.getId());
            }

            case SHIPPER -> false; // Shippers cannot access vehicles for GPS updates
        };
    }

    @Override
    public boolean isTripVehicleConsistent(Trip trip, Vehicle vehicle, User user) {
        if (trip == null || vehicle == null || user == null) {
            return false;
        }

        // Verify trip uses this vehicle
        Long tripVehicleId = trip.getBooking().getVehicle().getId();
        if (!tripVehicleId.equals(vehicle.getId())) {
            log.warn("Trip {} uses vehicle {} but GPS update requested for vehicle {}", 
                    trip.getId(), tripVehicleId, vehicle.getId());
            return false;
        }

        // Verify trip is assigned to this driver (for non-admin users)
        if (user.getRole() != User.Role.ADMIN) {
            Long tripDriverId = trip.getBooking().getDriver().getId();
            
            if (user.getRole() == User.Role.DRIVER && !tripDriverId.equals(user.getId())) {
                log.warn("Trip {} assigned to driver {} but GPS update from driver {}", 
                        trip.getId(), tripDriverId, user.getId());
                return false;
            }
            
            // For fleet owners, verify the driver belongs to their fleet
            if (user.getRole() == User.Role.FLEET_OWNER) {
                User tripDriver = trip.getBooking().getDriver();
                if (tripDriver.getFleetOwnerId() == null || 
                    !tripDriver.getFleetOwnerId().equals(user.getId())) {
                    log.warn("Trip {} driver {} not in fleet owned by user {}", 
                            trip.getId(), tripDriverId, user.getId());
                    return false;
                }
            }
        }

        return true;
    }

    @Override
    public boolean isTripTrackable(Trip trip) {
        if (trip == null) {
            return false;
        }

        // Only ongoing trips can receive GPS updates
        return Trip.TripStatus.ONGOING.equals(trip.getTripStatus());
    }

    /**
     * Find the ongoing trip associated with this vehicle and user.
     * Implements secure trip discovery based on user role.
     */
    private Trip findAuthorizedTrip(Vehicle vehicle, User user) {
        // Find ongoing trips using this vehicle
        Trip trip = switch (user.getRole()) {
            case ADMIN -> {
                // Admin can access any trip using this vehicle
                yield tripRepository.findByBooking_VehicleAndTripStatus(vehicle, Trip.TripStatus.ONGOING)
                        .stream()
                        .findFirst()
                        .orElseThrow(() -> TrackingAuthorizationException.tripNotFound(null));
            }

            case DRIVER -> {
                // Driver can only access their own trips
                yield tripRepository.findByBooking_DriverAndBooking_VehicleAndTripStatus(
                        user, vehicle, Trip.TripStatus.ONGOING)
                        .stream()
                        .findFirst()
                        .orElseThrow(() -> TrackingAuthorizationException.unauthorizedTripAccess(null, user.getId()));
            }

            case FLEET_OWNER -> {
                // Fleet owner can access trips by drivers in their fleet using their vehicles
                yield tripRepository.findByBooking_VehicleAndTripStatus(vehicle, Trip.TripStatus.ONGOING)
                        .stream()
                        .filter(t -> {
                            User driver = t.getBooking().getDriver();
                            return driver.getFleetOwnerId() != null && 
                                   driver.getFleetOwnerId().equals(user.getId());
                        })
                        .findFirst()
                        .orElseThrow(() -> TrackingAuthorizationException.unauthorizedTripAccess(null, user.getId()));
            }

            case SHIPPER -> {
                // Shippers cannot submit GPS updates
                throw TrackingAuthorizationException.userCannotSubmitTracking(user.getId(), user.getRole().name());
            }
        };

        if (trip == null) {
            throw TrackingAuthorizationException.tripNotFound(null);
        }

        return trip;
    }

    /**
     * Determine the authorization level based on vehicle ownership and user role.
     */
    private AuthorizationLevel determineAuthorizationLevel(Vehicle vehicle, User user) {
        return switch (user.getRole()) {
            case ADMIN -> AuthorizationLevel.ADMIN;
            
            case FLEET_OWNER -> AuthorizationLevel.FLEET_OWNER;
            
            case DRIVER -> {
                if (vehicle.getUserId().equals(user.getId())) {
                    yield AuthorizationLevel.OWNER;
                } else if (vehicle.getFleetOwnerId() != null && 
                          user.getFleetOwnerId() != null &&
                          vehicle.getFleetOwnerId().equals(user.getFleetOwnerId())) {
                    yield AuthorizationLevel.FLEET_MEMBER;
                } else {
                    // This shouldn't happen if authorization validation passed
                    throw TrackingAuthorizationException.unauthorizedVehicleAccess(vehicle.getId(), user.getId());
                }
            }
            
            case SHIPPER -> {
                // This shouldn't happen if authorization validation passed  
                throw TrackingAuthorizationException.userCannotSubmitTracking(user.getId(), user.getRole().name());
            }
        };
    }
}