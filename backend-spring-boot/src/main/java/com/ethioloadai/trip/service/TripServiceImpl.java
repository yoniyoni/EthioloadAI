package com.ethioloadai.trip.service;

import com.ethioloadai.booking.entity.Booking;
import com.ethioloadai.booking.repository.BookingRepository;
import com.ethioloadai.freight.entity.Freight;
import com.ethioloadai.tracking.dto.LocationUpdateRequest;
import com.ethioloadai.tracking.service.RouteBoundaryProtectionService;
import com.ethioloadai.tracking.service.VehicleTripConsistencyService;
import com.ethioloadai.trip.entity.Trip;
import com.ethioloadai.trip.entity.TripStop;
import com.ethioloadai.trip.exception.TripNotFoundException;
import com.ethioloadai.trip.exception.TripStatusException;
import com.ethioloadai.trip.exception.TripValidationException;
import com.ethioloadai.trip.repository.TripRepository;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.vehicle.entity.Vehicle;
import com.ethioloadai.vehicle.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class TripServiceImpl implements TripService {
    private static final Logger log = LoggerFactory.getLogger(TripServiceImpl.class);

    private final TripRepository tripRepository;
    private final BookingRepository bookingRepository;
    private final VehicleRepository vehicleRepository;
    private final TripInvariantService tripInvariantService;
    private final TripLifecycleService tripLifecycleService;
    private final TripStateMachine tripStateMachine;
    private final TripMonitoringService tripMonitoringService;
    private final TripStatusTransitionService tripStatusTransitionService;
    private final TripStopService tripStopService;
    private final RouteDataService routeDataService;
    private final TripSecurityService tripSecurityService;
    private final VehicleTripConsistencyService vehicleTripConsistencyService;
    private final RouteBoundaryProtectionService routeBoundaryProtectionService;

    // GPS breadcrumb limit to prevent unbounded growth
    private static final int MAX_GPS_BREADCRUMBS = 500;
    private static final DateTimeFormatter GPS_TIMESTAMP_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Override
    @Transactional
    public Trip createTripFromBooking(Long bookingId, User driver) {
        log.info("Creating trip from booking {} for driver {}", bookingId, driver.getId());

        // 1. Validate booking exists and belongs to driver
        Booking booking = bookingRepository.findByIdAndDriver_Id(bookingId, driver.getId())
                .orElseThrow(() -> TripValidationException.bookingNotFound(bookingId));

        // 2. Comprehensive invariant validation using dedicated service
        tripInvariantService.validateTripCreationEligibility(booking, driver);

        try {
            // 3. Create trip with data derived from booking
            Trip trip = createTripFromBookingData(booking);
            Trip savedTrip = tripRepository.save(trip);

            // 4. Update related entities
            updateBookingStatusForTrip(booking);
            updateFreightStatusForTrip(booking.getCargoRequest());

            // 5. Final consistency validation
            tripInvariantService.enforceTripStateConsistency(savedTrip);

            log.info("Trip {} created successfully for booking {}", savedTrip.getId(), bookingId);
            return savedTrip;

        } catch (DataIntegrityViolationException e) {
            // Handle race condition - another thread created trip for same booking
            if (e.getMessage() != null && e.getMessage().contains("trips_booking_id_unique")) {
                throw TripValidationException.tripAlreadyExists(bookingId);
            }
            throw e;
        }
    }

    @Override
    public Trip getTripWithAuthorization(Long tripId, User user) {
        // Use the security service for enhanced ownership validation
        return tripSecurityService.getAuthorizedTrip(tripId, user)
                .orElseThrow(() -> TripNotFoundException.withId(tripId));
    }

    @Override
    public Page<Trip> getDriverTrips(Long driverId, Pageable pageable) {
        // This method should validate user can access the driver's data
        User currentUser = getCurrentUser(); // Would need to inject authentication context
        return tripSecurityService.getDriverActiveTrips(driverId, currentUser, pageable);
    }

    @Override
    public String exportTripRouteData(Long tripId, String format, User user) {
        Trip trip = getTripWithAuthorization(tripId, user);
        return routeDataService.exportRouteData(trip, format);
    }

    // ── Enhanced Security Methods ────────────────────────────────────────────

    @Override
    public Page<Trip> getSecureTrips(User user, Pageable pageable) {
        log.debug("Getting secure trips for user {} (role: {})", user.getId(), user.getRole());
        return tripSecurityService.getAuthorizedTrips(user, pageable);
    }

    @Override
    public Page<Trip> searchSecureTrips(User user, TripSecurityService.TripSearchCriteria searchCriteria, Pageable pageable) {
        log.debug("Searching secure trips for user {}: {}", user.getId(), searchCriteria);
        return tripSecurityService.searchAuthorizedTrips(user, searchCriteria, pageable);
    }

    @Override
    public TripSecurityService.TripAccessValidation validateTripAccess(Long tripId, User user, TripSecurityService.AccessType accessType) {
        log.debug("Validating {} access to trip {} for user {}", accessType, tripId, user.getId());
        return tripSecurityService.validateTripAccess(tripId, user, accessType);
    }

    @Override
    public TripSecurityService.TripAnalytics getSecureTripAnalytics(User user, TripSecurityService.DateRange dateRange) {
        log.debug("Getting secure trip analytics for user {} between {} and {}", 
                 user.getId(), dateRange.startDate(), dateRange.endDate());
        return tripSecurityService.getAuthorizedTripAnalytics(user, dateRange);
    }

    @Override
    @Transactional
    public Trip updateTripStatus(Long tripId, Trip.TripStatus newStatus, User user) {
        // Delegate to the comprehensive transition service
        return tripStatusTransitionService.executeStatusTransition(tripId, newStatus, user);
    }

    @Override
    @Transactional
    public Trip completeTrip(Long tripId, User user) {
        // Use dedicated lifecycle service for completion logic
        return initializeResponseGraph(tripLifecycleService.completeTrip(tripId, user));
    }

    @Override
    @Transactional
    public Trip updateLocation(Long tripId, Map<String, Object> gpsData, User user) {
        // IMPORTANT: This method signature is legacy and doesn't include vehicleId
        // For vehicle consistency, we need explicit vehicleId in GPS updates
        // This method should be deprecated in favor of updateLocationWithVehicle()
        
        Trip trip = getTripWithAuthorization(tripId, user);
        
        // Convert map to GPS data object
        RouteDataService.GpsLocationData gpsLocationData = convertMapToGpsData(gpsData);
        
        // Use RouteDataService for safe GPS handling
        Trip updatedTrip = routeDataService.addGpsLocation(trip, gpsLocationData, user);
        
        // Apply route boundary protection after GPS update
        RouteBoundaryProtectionService.BoundaryEnforcementResult boundaryResult = 
            routeBoundaryProtectionService.enforceRouteBoundaries(updatedTrip);
        
        if (boundaryResult.boundariesExceeded()) {
            log.info("Route boundaries enforced for trip {}: {}", 
                    tripId, String.join(", ", boundaryResult.actionsPerformed()));
        }
        
        return initializeResponseGraph(updatedTrip);
    }

    @Override
    @Transactional
    public Trip updateLocationWithVehicleValidation(Long tripId, LocationUpdateRequest locationRequest, User user) {
        log.info("Updating location for trip {} from vehicle {} by user {}", 
                tripId, locationRequest.getVehicleId(), user.getId());
        
        // Validate vehicle-trip consistency FIRST
        VehicleTripConsistencyService.VehicleConsistencyValidation validation = 
            vehicleTripConsistencyService.validateLocationUpdate(tripId, locationRequest, user);
        
        if (!validation.isValid()) {
            log.warn("Vehicle consistency validation failed: {}", validation.getReason());
            throw new com.ethioloadai.tracking.exception.VehicleConsistencyException(validation);
        }
        
        // Get trip after validation passes
        Trip trip = getTripWithAuthorization(tripId, user);
        
        // Convert LocationUpdateRequest to GPS data
        RouteDataService.GpsLocationData gpsLocationData = convertLocationRequestToGpsData(locationRequest);
        
        // Use RouteDataService for safe GPS handling
        Trip updatedTrip = routeDataService.addGpsLocation(trip, gpsLocationData, user);
        
        log.debug("Successfully updated location for trip {} from vehicle {}", 
                 tripId, locationRequest.getVehicleId());
        
        return updatedTrip;
    }

    @Override
    public Trip getTripLocation(Long tripId, User user) {
        return getTripWithAuthorization(tripId, user);
    }

    @Override
    public boolean isValidStatusTransition(Trip.TripStatus from, Trip.TripStatus to) {
        return tripStateMachine.isValidTransition(from, to);
    }

    @Override
    @Transactional
    public Trip startTrip(Long tripId, User user) {
        log.info("Starting trip {} for user {}", tripId, user.getId());
        return initializeResponseGraph(tripLifecycleService.startTrip(tripId, user));
    }

    @Override
    @Transactional
    public Trip markArrived(Long tripId, User user) {
        log.info("Marking arrived for trip {} by user {}", tripId, user.getId());
        return initializeResponseGraph(tripLifecycleService.markArrived(tripId, user));
    }

    @Override
    @Transactional
    public Trip markDeliveryPendingVerification(Long tripId) {
        return initializeResponseGraph(tripLifecycleService.markDeliveryPendingVerification(tripId));
    }

    @Override
    @Transactional
    public Trip revertToArrived(Long tripId) {
        return initializeResponseGraph(tripLifecycleService.revertToArrived(tripId));
    }

    @Override
    @Transactional
    public Trip completeTripAfterVerification(Long tripId) {
        return initializeResponseGraph(tripLifecycleService.completeTripAfterVerification(tripId));
    }

    @Override
    public TripLifecycleService.TripProgress getTripProgress(Long tripId, User user) {
        Trip trip = getTripWithAuthorization(tripId, user);
        return tripLifecycleService.getTripProgress(trip);
    }

    @Override
    public List<Trip> getTripsRequiringAttention(User user) {
        if (!isAdmin(user)) {
            throw TripValidationException.bookingNotEligible("only admin users can view trips requiring attention");
        }
        
        return tripLifecycleService.getTripsRequiringAttention();
    }

    @Override
    public Map<String, Object> getTripStatistics(User user) {
        if (!isAdmin(user)) {
            throw TripValidationException.bookingNotEligible("only admin users can view trip statistics");
        }
        
        return tripMonitoringService.getTripStatistics();
    }

    @Override
    public TripMonitoringService.TripSystemHealth getTripSystemHealth(User user) {
        if (!isAdmin(user)) {
            throw TripValidationException.bookingNotEligible("only admin users can view system health");
        }
        
        return tripMonitoringService.checkTripSystemHealth();
    }

    @Override
    @Transactional
    public Trip executeStatusTransition(Long tripId, Trip.TripStatus targetStatus, User user) {
        return tripStatusTransitionService.executeStatusTransition(tripId, targetStatus, user);
    }

    @Override
    public List<TripStatusTransitionService.StatusOption> getAvailableTransitions(Long tripId, User user) {
        Trip trip = getTripWithAuthorization(tripId, user);
        return tripStatusTransitionService.getAvailableTransitions(trip, user);
    }

    @Override
    public TripStatusTransitionService.TransitionValidationResult validateStatusTransition(
            Long tripId, Trip.TripStatus targetStatus, User user) {
        Trip trip = getTripWithAuthorization(tripId, user);
        return tripStatusTransitionService.validateTransition(trip, targetStatus, user);
    }

    @Override
    @Transactional
    public List<Trip> processAutomaticTransitions(User user) {
        if (!isAdmin(user)) {
            throw TripValidationException.bookingNotEligible("only admin users can process automatic transitions");
        }
        
        return tripStatusTransitionService.processAutomaticTransitions();
    }

    // ── Multi-Stop Management ────────────────────────────────────────────────

    @Override
    @Transactional
    public com.ethioloadai.trip.entity.TripStop addTripStop(Long tripId, String locationName, Integer stopOrder,
                                                            java.math.BigDecimal pickupLat, java.math.BigDecimal pickupLng,
                                                            java.math.BigDecimal agreedPrice, Long cargoRequestId,
                                                            String notes, User user) {
        log.debug("Adding stop to trip {} by user {}: {}", tripId, user.getId(), locationName);
        return tripStopService.addStop(tripId, locationName, stopOrder, pickupLat, pickupLng,
                                      agreedPrice, cargoRequestId, notes, user);
    }

    @Override
    @Transactional
    public com.ethioloadai.trip.entity.TripStop updateTripStop(Long stopId, String locationName,
                                                              java.math.BigDecimal pickupLat, java.math.BigDecimal pickupLng,
                                                              java.math.BigDecimal agreedPrice, String notes, User user) {
        log.debug("Updating stop {} by user {}", stopId, user.getId());
        return tripStopService.updateStop(stopId, locationName, pickupLat, pickupLng, agreedPrice, notes, user);
    }

    @Override
    @Transactional
    public void removeTripStop(Long stopId, User user) {
        log.debug("Removing stop {} by user {}", stopId, user.getId());
        tripStopService.removeStop(stopId, user);
    }

    @Override
    public List<com.ethioloadai.trip.entity.TripStop> getTripStops(Long tripId, User user) {
        log.debug("Getting stops for trip {} by user {}", tripId, user.getId());
        return tripStopService.getTripStops(tripId, user);
    }

    @Override
    @Transactional
    public com.ethioloadai.trip.entity.TripStop updateTripStopStatus(Long stopId, com.ethioloadai.trip.entity.TripStop.StopStatus newStatus, User user) {
        log.debug("Updating stop {} status to {} by user {}", stopId, newStatus, user.getId());
        return tripStopService.updateStopStatus(stopId, newStatus, user);
    }

    @Override
    @Transactional
    public List<com.ethioloadai.trip.entity.TripStop> reorderTripStops(Long tripId, List<Long> newOrdering, User user) {
        log.debug("Reordering stops for trip {} by user {}: {}", tripId, user.getId(), newOrdering);
        return tripStopService.reorderStops(tripId, newOrdering, user);
    }

    // ── Route Data Management ────────────────────────────────────────────────

    @Override
    public RouteDataService.GpsLocationData getCurrentTripLocation(Long tripId, User user) {
        Trip trip = getTripWithAuthorization(tripId, user);
        return routeDataService.getCurrentLocation(trip);
    }

    @Override
    public List<RouteDataService.GpsLocationData> getTripRouteHistory(Long tripId, Integer limit, User user) {
        Trip trip = getTripWithAuthorization(tripId, user);
        return routeDataService.getRouteHistory(trip, limit, null, null);
    }

    @Override
    public RouteDataService.RouteAnalytics getTripRouteAnalytics(Long tripId, User user) {
        Trip trip = getTripWithAuthorization(tripId, user);
        return routeDataService.getRouteAnalytics(trip);
    }

    @Override
    @Transactional
    public int archiveTripRouteData(Long tripId, User user) {
        if (!isAdmin(user)) {
            throw TripValidationException.bookingNotEligible("only admin users can archive route data");
        }
        Trip trip = getTripWithAuthorization(tripId, user);
        return routeDataService.archiveOldGpsData(trip);
    }

    // ── Private Helper Methods ───────────────────────────────────────────────

    private RouteDataService.GpsLocationData convertMapToGpsData(Map<String, Object> gpsData) {
        // Add timestamp if not provided
        if (!gpsData.containsKey("ts")) {
            gpsData.put("ts", LocalDateTime.now().format(GPS_TIMESTAMP_FORMAT));
        }

        BigDecimal latitude = gpsData.containsKey("lat") ? 
            new BigDecimal(gpsData.get("lat").toString()) : null;
        BigDecimal longitude = gpsData.containsKey("lng") ? 
            new BigDecimal(gpsData.get("lng").toString()) : null;
        BigDecimal altitude = gpsData.containsKey("alt") ? 
            new BigDecimal(gpsData.get("alt").toString()) : null;
        BigDecimal accuracy = gpsData.containsKey("accuracy") ? 
            new BigDecimal(gpsData.get("accuracy").toString()) : null;
        BigDecimal speed = gpsData.containsKey("speed") ? 
            new BigDecimal(gpsData.get("speed").toString()) : null;
        BigDecimal heading = gpsData.containsKey("heading") ? 
            new BigDecimal(gpsData.get("heading").toString()) : null;
        
        LocalDateTime timestamp = LocalDateTime.parse(gpsData.get("ts").toString(), GPS_TIMESTAMP_FORMAT);

        Map<String, Object> metadata = new HashMap<>(gpsData);
        // Remove known GPS fields from metadata
        metadata.remove("lat");
        metadata.remove("lng");
        metadata.remove("ts");
        metadata.remove("alt");
        metadata.remove("accuracy");
        metadata.remove("speed");
        metadata.remove("heading");

        return new RouteDataService.GpsLocationData(
            latitude, longitude, altitude, accuracy, speed, heading, timestamp, metadata
        );
    }

    private RouteDataService.GpsLocationData convertLocationRequestToGpsData(LocationUpdateRequest locationRequest) {
        // Validate required fields
        if (locationRequest.getLatitude() == null || locationRequest.getLongitude() == null) {
            throw TripValidationException.invalidGpsData("Latitude and longitude are required");
        }
        if (locationRequest.getTimestamp() == null) {
            throw TripValidationException.invalidGpsData("GPS timestamp is required");
        }

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("vehicleId", locationRequest.getVehicleId());
        
        return new RouteDataService.GpsLocationData(
            locationRequest.getLatitude(),
            locationRequest.getLongitude(),
            null, // altitude not provided in LocationUpdateRequest
            locationRequest.getAccuracy(),
            locationRequest.getSpeed(),
            locationRequest.getHeading(),
            locationRequest.getTimestamp(),
            metadata
        );
    }

    private Trip createTripFromBookingData(Booking booking) {
        Freight freight = booking.getCargoRequest();
        
        // Use state machine to get proper initial status
        Trip.TripStatus initialStatus = tripStateMachine.getInitialStatus();
        
        return Trip.builder()
                .booking(booking)
            .driver(booking.getDriver())
            .vehicle(booking.getVehicle())
                .startLocation(freight.getPickupLocation())
                .destination(freight.getDestination())
                .routeData(new ArrayList<>()) // Empty GPS breadcrumb list
                .tripStatus(initialStatus)
                .tripType(Trip.TripType.SINGLE) // Default to single, will be updated when stops are added
                .totalStops(1)
                .completedStops(0)
                // Phase 7: startTime is set when driver explicitly calls startTrip().
                // Do NOT set it here — the idempotency check in startTrip looks for startTime != null.
                // .startTime(LocalDateTime.now())
                .build();
    }

    private Trip initializeResponseGraph(Trip trip) {
        Hibernate.initialize(trip.getBooking());
        if (trip.getBooking() != null) {
            Hibernate.initialize(trip.getBooking().getDriver());
            Hibernate.initialize(trip.getBooking().getVehicle());
            Hibernate.initialize(trip.getBooking().getCargoRequest());
            if (trip.getBooking().getCargoRequest() != null) {
                Hibernate.initialize(trip.getBooking().getCargoRequest().getShipper());
            }
        }
        Hibernate.initialize(trip.getTripStops());
        return trip;
    }

    private void updateBookingStatusForTrip(Booking booking) {
        // Booking status doesn't change when trip is created
        // Trip creation indicates the job has started
        log.debug("Trip created for booking {} - booking status remains {}", 
                 booking.getId(), booking.getBookingStatus());
    }

    private void updateFreightStatusForTrip(Freight freight) {
        // Update freight status to indicate it's being transported
        if (freight.getStatus() == Freight.FreightStatus.MATCHED) {
            freight.setStatus(Freight.FreightStatus.MATCHED); // Keep as matched during transport
            log.debug("Freight {} status remains MATCHED during trip", freight.getId());
        }
    }

    private boolean isAdmin(User user) {
        return user.getRole() == User.Role.ADMIN;
    }

    private User getCurrentUser() {
        // In production, this would get the user from Spring Security context
        // For now, return a placeholder - this would be injected via SecurityContextHolder
        throw new UnsupportedOperationException("getCurrentUser() needs SecurityContext integration");
    }
}