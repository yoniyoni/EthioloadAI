package com.ethioloadai.trip.service;

import com.ethioloadai.trip.entity.Trip;
import com.ethioloadai.trip.entity.TripStop;
import com.ethioloadai.trip.repository.TripRepository;
import com.ethioloadai.trip.repository.TripStopRepository;
import com.ethioloadai.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TripSecurityServiceImpl implements TripSecurityService {
    private static final Logger log = LoggerFactory.getLogger(TripSecurityServiceImpl.class);

    private final TripRepository tripRepository;
    private final TripStopRepository tripStopRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<Trip> getAuthorizedTrip(Long tripId, User user) {
        log.debug("Getting authorized trip {} for user {} (role: {})", tripId, user.getId(), user.getRole());

        Optional<Trip> authorizedTrip = switch (user.getRole()) {
            case ADMIN -> {
                // Admins can access any trip
                Optional<Trip> trip = tripRepository.findByIdWithStops(tripId);
                log.debug("Admin access to trip {}: {}", tripId, trip.isPresent() ? "granted" : "not found");
                yield trip;
            }
            case DRIVER -> {
                // Drivers can access trips they are assigned to
                Optional<Trip> trip = tripRepository.findByIdAndDriverId(tripId, user.getId());
                log.debug("Driver access to trip {}: {}", tripId, trip.isPresent() ? "granted" : "denied/not found");
                yield trip;
            }
            case SHIPPER -> {
                // Shippers can access trips for their cargo
                Optional<Trip> trip = tripRepository.findByIdAndShipperId(tripId, user.getId());
                if (trip.isEmpty()) {
                    // Also check stops for multi-shipper scenarios
                    trip = tripRepository.findByIdWithStops(tripId)
                            .filter(t -> hasShipperAccess(t, user.getId()));
                }
                log.debug("Shipper access to trip {}: {}", tripId, trip.isPresent() ? "granted" : "denied/not found");
                yield trip;
            }
            case FLEET_OWNER -> {
                // Fleet owners can access trips for their fleet vehicles (PLACEHOLDER)
                // TODO: Implement fleet relationship checking
                Optional<Trip> trip = Optional.empty(); // tripRepository.findByIdAndFleetOwnerId(tripId, user.getId());
                log.debug("Fleet owner access to trip {}: {}", tripId, trip.isPresent() ? "granted" : "denied/not found");
                yield trip;
            }
        };
        authorizedTrip.ifPresent(this::initializeResponseGraph);
        return authorizedTrip;
    }

    private void initializeResponseGraph(Trip trip) {
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
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Trip> getAuthorizedTrips(User user, Pageable pageable) {
        log.debug("Getting authorized trips for user {} (role: {}, page: {}, size: {})", 
                 user.getId(), user.getRole(), pageable.getPageNumber(), pageable.getPageSize());

        Page<Trip> trips = switch (user.getRole()) {
            case ADMIN -> tripRepository.findAllWithBasicDetails(pageable);
            case DRIVER -> tripRepository.findByDriverId(user.getId(), pageable);
            case SHIPPER -> tripRepository.findByShipperId(user.getId(), pageable);
            case FLEET_OWNER -> Page.empty();
        };

        log.debug("Found {} trips for user {}", trips.getTotalElements(), user.getId());
        return trips;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Trip> getAuthorizedTripsByStatus(User user, Trip.TripStatus status, Pageable pageable) {
        log.debug("Getting authorized trips with status {} for user {}", status, user.getId());

        Page<Trip> trips = switch (user.getRole()) {
            case ADMIN -> tripRepository.findByTripStatus(status, pageable);
            case DRIVER -> tripRepository.findByDriverIdAndTripStatus(user.getId(), status, pageable);
            case SHIPPER -> tripRepository.findByShipperIdAndTripStatus(user.getId(), status, pageable);
            case FLEET_OWNER -> Page.empty();
        };

        log.debug("Found {} trips with status {} for user {}", trips.getTotalElements(), status, user.getId());
        return trips;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Trip> getAuthorizedTripsByDateRange(User user, LocalDateTime startDate, 
                                                   LocalDateTime endDate, Pageable pageable) {
        log.debug("Getting authorized trips between {} and {} for user {}", startDate, endDate, user.getId());

        Page<Trip> trips = switch (user.getRole()) {
            case ADMIN -> tripRepository.findByStartTimeBetween(startDate, endDate, pageable);
            case DRIVER -> tripRepository.findByDriverIdAndStartTimeBetween(user.getId(), startDate, endDate, pageable);
            case SHIPPER -> tripRepository.findByShipperIdAndStartTimeBetween(user.getId(), startDate, endDate, pageable);
            case FLEET_OWNER -> Page.empty();
        };

        log.debug("Found {} trips in date range for user {}", trips.getTotalElements(), user.getId());
        return trips;
    }

    @Override
    public Page<Trip> getDriverActiveTrips(Long driverId, User user, Pageable pageable) {
        log.debug("Getting active trips for driver {} requested by user {}", driverId, user.getId());

        // Validate user can access this driver's trips
        if (!canAccessDriverData(driverId, user)) {
            log.warn("User {} denied access to driver {} trips", user.getId(), driverId);
            return Page.empty(pageable);
        }

        Page<Trip> trips = tripRepository.findByDriverIdAndTripStatus(driverId, Trip.TripStatus.ONGOING, pageable);
        log.debug("Found {} active trips for driver {}", trips.getTotalElements(), driverId);
        return trips;
    }

    @Override
    public Page<Trip> getShipperTrips(Long shipperId, User user, Pageable pageable) {
        log.debug("Getting trips for shipper {} requested by user {}", shipperId, user.getId());

        // Validate user can access this shipper's trips
        if (!canAccessShipperData(shipperId, user)) {
            log.warn("User {} denied access to shipper {} trips", user.getId(), shipperId);
            return Page.empty(pageable);
        }

        Page<Trip> trips = tripRepository.findByShipperId(shipperId, pageable);
        log.debug("Found {} trips for shipper {}", trips.getTotalElements(), shipperId);
        return trips;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TripStop> getAuthorizedTripStops(Long tripId, User user) {
        log.debug("Getting authorized trip stops for trip {} and user {}", tripId, user.getId());

        // First validate trip access
        Optional<Trip> trip = getAuthorizedTrip(tripId, user);
        if (trip.isEmpty()) {
            log.debug("User {} has no access to trip {}", user.getId(), tripId);
            return List.of();
        }

        List<TripStop> stops = tripStopRepository.findByTrip_IdOrderByStopOrderAsc(tripId);
        
        // Filter stops based on user role and multi-shipper scenarios
        if (user.getRole() == User.Role.SHIPPER) {
            stops = filterStopsForShipper(stops, user.getId());
        }

        log.debug("Found {} authorized stops for trip {} and user {}", stops.size(), tripId, user.getId());
        return stops;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Trip> searchAuthorizedTrips(User user, TripSearchCriteria criteria, Pageable pageable) {
        log.debug("Searching authorized trips for user {} with criteria: {}", user.getId(), criteria);

        // Build search results based on user role and criteria
        List<Trip> allTrips = switch (user.getRole()) {
            case ADMIN -> tripRepository.findAllWithBasicDetails(Pageable.unpaged()).getContent();
            case DRIVER -> tripRepository.findByDriverId(user.getId(), Pageable.unpaged()).getContent();
            case SHIPPER -> tripRepository.findByShipperId(user.getId(), Pageable.unpaged()).getContent();
            case FLEET_OWNER -> Collections.emptyList();
        };

        // Apply search filters
        List<Trip> filteredTrips = allTrips.stream()
                .filter(trip -> matchesSearchCriteria(trip, criteria))
                .collect(Collectors.toList());

        // Apply pagination
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), filteredTrips.size());
        List<Trip> pageContent = start >= filteredTrips.size() ? 
            List.of() : filteredTrips.subList(start, end);

        log.debug("Search returned {} trips for user {}", filteredTrips.size(), user.getId());
        return new PageImpl<>(pageContent, pageable, filteredTrips.size());
    }

    @Override
    @Transactional(readOnly = true)
    public TripAccessValidation validateTripAccess(Long tripId, User user, AccessType accessType) {
        log.debug("Validating {} access to trip {} for user {} (role: {})", 
                 accessType, tripId, user.getId(), user.getRole());

        Optional<Trip> tripOpt = tripRepository.findById(tripId);
        if (tripOpt.isEmpty()) {
            return new TripAccessValidation(
                tripId, false, accessType, "Trip not found", List.of(), null
            );
        }

        Trip trip = tripOpt.get();
        boolean hasAccess = false;
        List<String> permissions = new ArrayList<>();
        User.Role requiredRole = null;
        String reason = "";

        switch (user.getRole()) {
            case ADMIN -> {
                hasAccess = true;
                permissions.addAll(List.of("READ", "WRITE", "DELETE", "ADMIN", "GPS_UPDATE", "STATUS_CHANGE"));
                reason = "Admin has full access";
            }
            case DRIVER -> {
                if (isDriverOfTrip(trip, user.getId())) {
                    hasAccess = switch (accessType) {
                        case READ, GPS_UPDATE, STATUS_CHANGE -> true;
                        case WRITE -> trip.getTripStatus() == Trip.TripStatus.ONGOING;
                        case DELETE, ADMIN -> false;
                    };
                    permissions.addAll(List.of("READ", "GPS_UPDATE"));
                    if (trip.getTripStatus() == Trip.TripStatus.ONGOING) {
                        permissions.addAll(List.of("WRITE", "STATUS_CHANGE"));
                    }
                    reason = hasAccess ? "Driver of trip" : "Access denied for " + accessType;
                } else {
                    reason = "Not the assigned driver";
                    requiredRole = User.Role.DRIVER;
                }
            }
            case SHIPPER -> {
                if (isShipperOfTrip(trip, user.getId())) {
                    hasAccess = accessType == AccessType.READ;
                    permissions.add("READ");
                    reason = hasAccess ? "Shipper of trip" : "Shippers have read-only access";
                } else {
                    reason = "Not the shipper of this trip";
                    requiredRole = User.Role.SHIPPER;
                }
            }
            case FLEET_OWNER -> {
                // Fleet owners can access trips for their fleet vehicles
                // For now, implement similar to shipper (read-only access)
                hasAccess = accessType == AccessType.READ;
                permissions.add("READ");
                reason = hasAccess ? "Fleet owner access" : "Fleet owners have read-only access";
            }
        }

        log.debug("Access validation for trip {}: {} (reason: {})", tripId, hasAccess, reason);
        return new TripAccessValidation(tripId, hasAccess, accessType, reason, permissions, requiredRole);
    }

    @Override
    public Page<Trip> getTripsRequiringAttention(User user, Pageable pageable) {
        log.debug("Getting trips requiring attention for user {} (role: {})", user.getId(), user.getRole());

        // Base criteria for attention: overdue trips, stuck trips, etc.
        LocalDateTime overdueThreshold = LocalDateTime.now().minusHours(48);

        Page<Trip> trips = switch (user.getRole()) {
            case ADMIN -> tripRepository.findTripsRequiringAdminAttention(overdueThreshold, pageable);
            case DRIVER -> tripRepository.findDriverTripsRequiringAttention(user.getId(), overdueThreshold, pageable);
            case SHIPPER -> tripRepository.findShipperTripsRequiringAttention(user.getId(), overdueThreshold, pageable);
            case FLEET_OWNER -> Page.empty();
        };

        log.debug("Found {} trips requiring attention for user {}", trips.getTotalElements(), user.getId());
        return trips;
    }

    @Override
    @Transactional(readOnly = true)
    public TripAnalytics getAuthorizedTripAnalytics(User user, DateRange dateRange) {
        log.debug("Getting trip analytics for user {} between {} and {}", 
                 user.getId(), dateRange.startDate(), dateRange.endDate());

        List<Trip> trips = switch (user.getRole()) {
            case ADMIN -> tripRepository.findByStartTimeBetween(
                dateRange.startDate(), dateRange.endDate(), Pageable.unpaged()).getContent();
            case DRIVER -> tripRepository.findByDriverIdAndStartTimeBetween(
                user.getId(), dateRange.startDate(), dateRange.endDate(), Pageable.unpaged()).getContent();
            case SHIPPER -> tripRepository.findByShipperIdAndStartTimeBetween(
                user.getId(), dateRange.startDate(), dateRange.endDate(), Pageable.unpaged()).getContent();
            case FLEET_OWNER -> Collections.emptyList();
        };

        return calculateTripAnalytics(trips, user);
    }

    @Override
    public List<TripAccessValidation> validateBulkTripAccess(List<Long> tripIds, User user, AccessType accessType) {
        log.debug("Validating bulk {} access to {} trips for user {}", accessType, tripIds.size(), user.getId());

        return tripIds.stream()
                .map(tripId -> validateTripAccess(tripId, user, accessType))
                .collect(Collectors.toList());
    }

    // ── Private Helper Methods ───────────────────────────────────────────────

    private boolean hasShipperAccess(Trip trip, Long shipperId) {
        // Check if shipper is the primary cargo owner
        if (trip.getBooking().getCargoRequest().getShipper().getId().equals(shipperId)) {
            return true;
        }

        // Check if shipper has cargo in any of the stops (multi-shipper scenario)
        return trip.getTripStops().stream()
                .anyMatch(stop -> stop.getCargoRequest() != null && 
                         stop.getCargoRequest().getShipper().getId().equals(shipperId));
    }

    private boolean canAccessDriverData(Long driverId, User user) {
        return switch (user.getRole()) {
            case ADMIN -> true;
            case DRIVER -> user.getId().equals(driverId); // Drivers can only access their own data
            case SHIPPER -> false; // Shippers cannot access driver data directly
            case FLEET_OWNER -> {
                // Fleet owners can access data for their fleet drivers
                // This would need a fleet relationship check - for now, return false
                yield false;
            }
        };
    }

    private boolean canAccessShipperData(Long shipperId, User user) {
        return switch (user.getRole()) {
            case ADMIN -> true;
            case SHIPPER -> user.getId().equals(shipperId); // Shippers can only access their own data
            case DRIVER -> false; // Drivers cannot access shipper data directly
            case FLEET_OWNER -> false; // Fleet owners cannot access shipper data directly
        };
    }

    private List<TripStop> filterStopsForShipper(List<TripStop> stops, Long shipperId) {
        // In multi-shipper scenarios, show only stops belonging to this shipper
        return stops.stream()
                .filter(stop -> stop.getCargoRequest() == null || 
                               stop.getCargoRequest().getShipper().getId().equals(shipperId))
                .collect(Collectors.toList());
    }

    private boolean matchesSearchCriteria(Trip trip, TripSearchCriteria criteria) {
        // Location query
        if (criteria.locationQuery() != null && !criteria.locationQuery().trim().isEmpty()) {
            String query = criteria.locationQuery().toLowerCase();
            boolean matchesLocation = trip.getStartLocation().toLowerCase().contains(query) ||
                                    trip.getDestination().toLowerCase().contains(query);
            if (!matchesLocation) return false;
        }

        // Status filter
        if (criteria.status() != null && !criteria.status().equals(trip.getTripStatus())) {
            return false;
        }

        // Type filter
        if (criteria.type() != null && !criteria.type().equals(trip.getTripType())) {
            return false;
        }

        // Date range
        if (criteria.startDate() != null && trip.getStartTime().isBefore(criteria.startDate())) {
            return false;
        }
        if (criteria.endDate() != null && trip.getStartTime().isAfter(criteria.endDate())) {
            return false;
        }

        // Cargo request filter
        if (criteria.cargoRequestId() != null && 
            !criteria.cargoRequestId().equals(trip.getBooking().getCargoRequest().getId())) {
            return false;
        }

        // Vehicle filter
        if (criteria.vehicleId() != null && 
            !criteria.vehicleId().equals(trip.getBooking().getVehicle().getId())) {
            return false;
        }

        return true;
    }

    private boolean isDriverOfTrip(Trip trip, Long userId) {
        return trip.getBooking().getDriver().getId().equals(userId);
    }

    private boolean isShipperOfTrip(Trip trip, Long userId) {
        // Primary shipper
        if (trip.getBooking().getCargoRequest().getShipper().getId().equals(userId)) {
            return true;
        }

        // Multi-shipper scenarios
        return trip.getTripStops().stream()
                .anyMatch(stop -> stop.getCargoRequest() != null && 
                         stop.getCargoRequest().getShipper().getId().equals(userId));
    }

    private TripAnalytics calculateTripAnalytics(List<Trip> trips, User user) {
        if (trips.isEmpty()) {
            return new TripAnalytics(0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, 
                                   BigDecimal.ZERO, Map.of());
        }

        int totalTrips = trips.size();
        int ongoingTrips = (int) trips.stream()
                .mapToLong(t -> t.getTripStatus() == Trip.TripStatus.ONGOING ? 1 : 0)
                .sum();
        int completedTrips = (int) trips.stream()
                .mapToLong(t -> t.getTripStatus() == Trip.TripStatus.COMPLETED ? 1 : 0)
                .sum();

        BigDecimal totalDistance = trips.stream()
                .map(this::calculateTripDistance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal averageDuration = calculateAverageDuration(trips);
        BigDecimal completionRate = totalTrips > 0 ? 
            new BigDecimal(completedTrips).divide(new BigDecimal(totalTrips), 2, RoundingMode.HALF_UP) :
            BigDecimal.ZERO;

        Map<String, Object> roleSpecificMetrics = calculateRoleSpecificMetrics(trips, user);

        return new TripAnalytics(totalTrips, ongoingTrips, completedTrips, totalDistance, 
                               averageDuration, completionRate, roleSpecificMetrics);
    }

    private BigDecimal calculateTripDistance(Trip trip) {
        // Simplified distance calculation - would use RouteDataService in practice
        return new BigDecimal("100.0"); // Mock 100km average
    }

    private BigDecimal calculateAverageDuration(List<Trip> trips) {
        // Simplified duration calculation
        return new BigDecimal("8.5"); // Mock 8.5 hours average
    }

    private Map<String, Object> calculateRoleSpecificMetrics(List<Trip> trips, User user) {
        Map<String, Object> metrics = new HashMap<>();
        
        switch (user.getRole()) {
            case ADMIN -> {
                metrics.put("total_drivers", trips.stream()
                    .map(t -> t.getBooking().getDriver().getId())
                    .distinct().count());
                metrics.put("system_utilization", 0.85); // Mock 85% utilization
            }
            case DRIVER -> {
                metrics.put("earnings_estimate", trips.stream()
                    .mapToDouble(t -> 5000.0) // Mock earnings
                    .sum());
                metrics.put("average_rating", 4.7); // Mock rating
            }
            case SHIPPER -> {
                metrics.put("total_shipments", trips.size());
                metrics.put("on_time_delivery_rate", 0.92); // Mock 92% on-time rate
            }
        }
        
        return metrics;
    }
}