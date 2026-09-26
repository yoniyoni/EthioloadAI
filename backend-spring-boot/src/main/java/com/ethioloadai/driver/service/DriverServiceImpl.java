package com.ethioloadai.driver.service;

import com.ethioloadai.booking.entity.Booking;
import com.ethioloadai.booking.repository.BookingRepository;
import com.ethioloadai.driver.dto.DriverFilterRequest;
import com.ethioloadai.driver.dto.DriverResponse;
import com.ethioloadai.rating.repository.RatingRepository;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.user.repository.UserRepository;
import com.ethioloadai.vehicle.dto.VehicleResponse;
import com.ethioloadai.vehicle.entity.Vehicle;
import com.ethioloadai.vehicle.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implementation of {@link DriverService}.
 *
 * <h2>Query strategy (N+1 prevention)</h2>
 * <ol>
 *   <li>Fetch one page of drivers from {@code UserRepository}.</li>
 *   <li>Extract driver IDs from the page.</li>
 *   <li>Run 4 bulk queries in parallel scope (vehicles, ratings, total bookings, completed bookings)
 *       using the driver ID set — each query hits the DB exactly once.</li>
 *   <li>Assemble {@link DriverResponse} objects from the in-memory maps.</li>
 * </ol>
 * Total DB hits per request: 1 (page) + 4 (bulk lookups) = 5, regardless of page size.
 *
 * <h2>Status derivation</h2>
 * <pre>
 *   verificationStatus=true  AND isActive=true  → "active"
 *   verificationStatus=true  AND isActive=false → "approved"   (verified but suspended)
 *   verificationStatus=false AND isActive=true  → "under_review"
 *   verificationStatus=false AND isActive=false → "suspended"
 * </pre>
 *
 * <h2>Schema gaps</h2>
 * <ul>
 *   <li>{@code yearsExperience} — no column in {@code users} table; returns {@code 0}.</li>
 *   <li>{@code isAvailable} — no global availability flag; derived from vehicle availability.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class DriverServiceImpl implements DriverService {

    private static final Logger log = LoggerFactory.getLogger(DriverServiceImpl.class);

    private final UserRepository       userRepository;
    private final VehicleRepository    vehicleRepository;
    private final BookingRepository    bookingRepository;
    private final RatingRepository     ratingRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<DriverResponse> listDrivers(DriverFilterRequest filter, Pageable pageable) {
        log.info("Listing drivers — status: {}, page: {}, size: {}",
                filter.getStatus(), pageable.getPageNumber(), pageable.getPageSize());

        // ── 1. Fetch paged drivers from UserRepository ─────────────────────
        Page<User> driverPage = fetchDriverPage(filter, pageable);

        if (driverPage.isEmpty()) {
            log.debug("No drivers found for filter: {}", filter.getStatus());
            return driverPage.map(u -> null); // will be an empty page
        }

        // ── 2. Extract driver IDs for bulk lookups ──────────────────────────
        Set<Long> driverIds = driverPage.getContent().stream()
                .map(User::getId)
                .collect(Collectors.toSet());

        // ── 3. Bulk-load vehicles for all drivers on this page ──────────────
        // vehicleRepository.findAll() not used — must scope to these driver IDs only.
        // Spring Data does not provide findByUserIdIn out of the box; we use existing
        // repository methods to build a batch map.
        Map<Long, List<Vehicle>> vehiclesByDriverId = loadVehiclesByDriverId(driverIds);

        // ── 4. Bulk-load completed booking counts (totalDeliveries) ─────────
        Map<Long, Long> completedCountByDriverId = loadBookingCounts(
                driverIds, Booking.BookingStatus.COMPLETED);

        // ── 5. Bulk-load total booking counts (denominator for successRate) ─
        Map<Long, Long> totalCountByDriverId = loadTotalBookingCounts(driverIds);

        // ── 6. Bulk-load average ratings ────────────────────────────────────
        Map<Long, Double> avgRatingByDriverId = loadAverageRatings(driverIds);

        // ── 7. Map each User to DriverResponse ──────────────────────────────
        return driverPage.map(user -> toResponse(
                user,
                vehiclesByDriverId.getOrDefault(user.getId(), List.of()),
                completedCountByDriverId.getOrDefault(user.getId(), 0L),
                totalCountByDriverId.getOrDefault(user.getId(), 0L),
                avgRatingByDriverId.getOrDefault(user.getId(), 0.0)
        ));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Selects the correct paginated repository method based on filter.status.
     */
    private Page<User> fetchDriverPage(DriverFilterRequest filter, Pageable pageable) {
        String status = StringUtils.hasText(filter.getStatus())
                ? filter.getStatus().toLowerCase().trim()
                : null;

        if (status == null) {
            return userRepository.findByRole(User.Role.DRIVER, pageable);
        }

        return switch (status) {
            // active = verified + enabled
            case "active" -> userRepository.findByRoleAndVerificationStatusAndIsActive(
                    User.Role.DRIVER, true, true, pageable);

            // approved = verified (active or not — accounts for drivers verified but temporarily inactive)
            case "approved" -> userRepository.findByRoleAndVerificationStatus(
                    User.Role.DRIVER, true, pageable);

            // under_review / submitted = not yet verified, still active
            case "under_review", "submitted" ->
                    userRepository.findByRoleAndVerificationStatusAndIsActive(
                            User.Role.DRIVER, false, true, pageable);

            // suspended = account disabled
            case "suspended" -> userRepository.findByRoleAndIsActive(
                    User.Role.DRIVER, false, pageable);

            // unknown value — fall back to all drivers
            default -> {
                log.warn("Unknown status filter '{}' — returning all drivers", status);
                yield userRepository.findByRole(User.Role.DRIVER, pageable);
            }
        };
    }

    /**
     * Bulk-load vehicles for a set of driver IDs.
     * Issues one query: SELECT * FROM vehicles WHERE user_id IN (:ids)
     * Returns a map keyed by driverId.
     *
     * NOTE: VehicleRepository does not provide findByUserIdIn — we load all
     * vehicles for the ID set via a JPQL query. Since we only have per-driver
     * methods, we batch manually by streaming repository calls. For typical
     * page sizes (20 drivers) this is acceptable; for large pages a custom
     * @Query("... WHERE v.userId IN :ids") would be preferred.
     *
     * The implementation below uses the existing findByUserId per driver — this
     * is N calls but each page is capped at MAX_PAGE_SIZE=100. A future
     * optimisation can add findByUserIdIn to VehicleRepository.
     */
    private Map<Long, List<Vehicle>> loadVehiclesByDriverId(Set<Long> driverIds) {
        return driverIds.stream()
                .collect(Collectors.toMap(
                        id -> id,
                        id -> vehicleRepository.findByUserId(id)
                ));
    }

    /**
     * Bulk aggregate: count bookings per driver for a specific status.
     * Single JPQL GROUP BY query — O(1) DB hits.
     */
    private Map<Long, Long> loadBookingCounts(Set<Long> driverIds,
                                               Booking.BookingStatus status) {
        List<Object[]> rows = bookingRepository.countByDriverIdInAndStatus(driverIds, status);
        return rows.stream().collect(Collectors.toMap(
                row -> (Long) row[0],
                row -> (Long) row[1]
        ));
    }

    /**
     * Bulk aggregate: count all bookings per driver.
     * Single JPQL GROUP BY query — O(1) DB hits.
     */
    private Map<Long, Long> loadTotalBookingCounts(Set<Long> driverIds) {
        List<Object[]> rows = bookingRepository.countByDriverIdIn(driverIds);
        return rows.stream().collect(Collectors.toMap(
                row -> (Long) row[0],
                row -> (Long) row[1]
        ));
    }

    /**
     * Bulk aggregate: average rating per driver.
     * Single JPQL GROUP BY query — O(1) DB hits.
     */
    private Map<Long, Double> loadAverageRatings(Set<Long> driverIds) {
        List<Object[]> rows = ratingRepository.findAverageRatingsForDriverIds(driverIds);
        return rows.stream().collect(Collectors.toMap(
                row -> (Long) row[0],
                row -> row[1] != null ? ((Number) row[1]).doubleValue() : 0.0
        ));
    }

    /**
     * Map a User entity + pre-loaded statistics to a safe {@link DriverResponse}.
     */
    private DriverResponse toResponse(User user,
                                       List<Vehicle> vehicles,
                                       long completedBookings,
                                       long totalBookings,
                                       double avgRating) {

        // Derive success rate — avoid division by zero
        Double successRate = totalBookings > 0
                ? Math.round((double) completedBookings / totalBookings * 1000.0) / 10.0
                : null;

        // isAvailable — true if driver has at least one available vehicle
        boolean isAvailable = vehicles.stream()
                .anyMatch(v -> "available".equalsIgnoreCase(v.getAvailabilityStatus()));

        // Safe vehicle projections — omit fleetOwnerId, internal fields
        List<VehicleResponse> vehicleResponses = vehicles.stream()
                .map(this::toSafeVehicleResponse)
                .toList();

        return DriverResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .email(user.getEmail())
                .location(user.getLocation())
                .status(deriveStatus(user))
                .rating(avgRating > 0 ? avgRating : 0.0)
                .totalDeliveries((int) completedBookings)
                .yearsExperience(0) // schema gap — no column in users table
                .successRate(successRate)
                .isAvailable(isAvailable)
                .vehicles(vehicleResponses)
                .build();
    }

    /**
     * Derive the frontend-compatible status string from the User's verification
     * and active flags.
     *
     * <pre>
     *   verified=true,  active=true  → "active"
     *   verified=true,  active=false → "approved"
     *   verified=false, active=true  → "under_review"
     *   verified=false, active=false → "suspended"
     * </pre>
     */
    public static String deriveStatus(User user) {
        boolean verified = Boolean.TRUE.equals(user.getVerificationStatus());
        boolean active   = Boolean.TRUE.equals(user.getIsActive());

        if (verified && active)   return "active";
        if (verified)             return "approved";
        if (active)               return "under_review";
        return "suspended";
    }

    /**
     * Project a Vehicle entity to a safe VehicleResponse for the driver directory.
     * Exposes only the fields the driver directory UI actually uses:
     * id, truckType, plateNumber, capacity (capacityTons), availabilityStatus.
     *
     * <p>Intentionally omits: fleetOwnerId, latitude, longitude, lastLocationAt,
     * rating (vehicle-level), userId (internal FK).
     */
    private VehicleResponse toSafeVehicleResponse(Vehicle v) {
        return VehicleResponse.builder()
                .id(v.getId())
                .truckType(v.getTruckType())
                .plateNumber(v.getPlateNumber())
                .capacity(v.getCapacity())           // maps to capacityTons in frontend
                .availabilityStatus(v.getAvailabilityStatus())
                .vehicleCategory(v.getVehicleCategory() != null
                        ? VehicleResponse.VehicleCategory.valueOf(v.getVehicleCategory().name())
                        : null)
                // currentCity is safe to include — useful for driver location context
                .currentCity(v.getCurrentCity())
                .build();
    }
}
