package com.ethioloadai.trip.service;

import com.ethioloadai.trip.entity.Trip;
import com.ethioloadai.trip.entity.TripStop;
import com.ethioloadai.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service for secure trip retrieval with comprehensive ownership validation and IDOR protection.
 * Implements multi-tenant data access patterns ensuring users only see authorized data.
 */
public interface TripSecurityService {

    /**
     * Get trip with strict ownership validation.
     * Prevents IDOR attacks by validating user access rights.
     *
     * @param tripId trip ID
     * @param user requesting user
     * @return trip if user has access, empty otherwise
     */
    Optional<Trip> getAuthorizedTrip(Long tripId, User user);

    /**
     * Get trips accessible to a user with role-based filtering.
     *
     * @param user requesting user
     * @param pageable pagination parameters
     * @return page of authorized trips
     */
    Page<Trip> getAuthorizedTrips(User user, Pageable pageable);

    /**
     * Get trips by status for authorized user.
     *
     * @param user requesting user
     * @param status trip status filter
     * @param pageable pagination parameters
     * @return page of authorized trips with specified status
     */
    Page<Trip> getAuthorizedTripsByStatus(User user, Trip.TripStatus status, Pageable pageable);

    /**
     * Get trips within date range for authorized user.
     *
     * @param user requesting user
     * @param startDate start date (inclusive)
     * @param endDate end date (inclusive)
     * @param pageable pagination parameters
     * @return page of authorized trips within date range
     */
    Page<Trip> getAuthorizedTripsByDateRange(User user, LocalDateTime startDate, 
                                           LocalDateTime endDate, Pageable pageable);

    /**
     * Get driver's active trips with security validation.
     *
     * @param driverId driver ID
     * @param user requesting user
     * @param pageable pagination parameters
     * @return active trips if user is authorized to view them
     */
    Page<Trip> getDriverActiveTrips(Long driverId, User user, Pageable pageable);

    /**
     * Get shipper's trips with multi-tenant isolation.
     *
     * @param shipperId shipper ID
     * @param user requesting user
     * @param pageable pagination parameters
     * @return shipper's trips if user is authorized to view them
     */
    Page<Trip> getShipperTrips(Long shipperId, User user, Pageable pageable);

    /**
     * Get trip stops with ownership validation.
     *
     * @param tripId trip ID
     * @param user requesting user
     * @return ordered list of stops if user has access
     */
    List<TripStop> getAuthorizedTripStops(Long tripId, User user);

    /**
     * Search trips with security filtering.
     *
     * @param user requesting user
     * @param query search query (location, reference, etc.)
     * @param pageable pagination parameters
     * @return search results filtered by user permissions
     */
    Page<Trip> searchAuthorizedTrips(User user, TripSearchCriteria query, Pageable pageable);

    /**
     * Validate if user can access specific trip data.
     *
     * @param tripId trip ID
     * @param user requesting user
     * @param accessType type of access required
     * @return access validation result
     */
    TripAccessValidation validateTripAccess(Long tripId, User user, AccessType accessType);

    /**
     * Get trips requiring user attention with role-based filtering.
     *
     * @param user requesting user
     * @param pageable pagination parameters
     * @return trips requiring attention based on user role
     */
    Page<Trip> getTripsRequiringAttention(User user, Pageable pageable);

    /**
     * Get comprehensive trip analytics for authorized user.
     *
     * @param user requesting user
     * @param dateRange date range for analytics
     * @return analytics data filtered by user permissions
     */
    TripAnalytics getAuthorizedTripAnalytics(User user, DateRange dateRange);

    /**
     * Validate bulk trip access for batch operations.
     *
     * @param tripIds list of trip IDs
     * @param user requesting user
     * @param accessType required access type
     * @return validation results for each trip
     */
    List<TripAccessValidation> validateBulkTripAccess(List<Long> tripIds, User user, AccessType accessType);

    // ── Enums and Value Objects ──────────────────────────────────────────────

    enum AccessType {
        READ,           // View trip details
        WRITE,          // Modify trip data
        DELETE,         // Delete trip
        ADMIN,          // Administrative access
        GPS_UPDATE,     // Update GPS location
        STATUS_CHANGE   // Change trip status
    }

    record TripSearchCriteria(
        String locationQuery,
        String referenceNumber,
        Trip.TripStatus status,
        Trip.TripType type,
        LocalDateTime startDate,
        LocalDateTime endDate,
        Long cargoRequestId,
        Long vehicleId
    ) {}

    record TripAccessValidation(
        Long tripId,
        boolean hasAccess,
        AccessType accessType,
        String reason,
        List<String> permissions,
        User.Role requiredRole
    ) {}

    record DateRange(
        LocalDateTime startDate,
        LocalDateTime endDate
    ) {}

    record TripAnalytics(
        int totalTrips,
        int ongoingTrips,
        int completedTrips,
        java.math.BigDecimal totalDistance,
        java.math.BigDecimal averageDuration,
        java.math.BigDecimal completionRate,
        java.util.Map<String, Object> roleSpecificMetrics
    ) {}
}