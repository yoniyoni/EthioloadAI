package com.ethioloadai.fleet.service;

import com.ethioloadai.booking.entity.Booking;
import com.ethioloadai.booking.repository.BookingRepository;
import com.ethioloadai.fleet.dto.*;
import com.ethioloadai.fleet.exception.FleetException;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.user.repository.UserRepository;
import com.ethioloadai.vehicle.entity.Vehicle;
import com.ethioloadai.vehicle.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Fleet management service implementation.
 *
 * <p>Acts as a thin orchestration layer over existing repositories.
 * Does NOT duplicate User/Vehicle/Booking/Payment service business logic.
 *
 * <p>Authorization model (enforced at service layer):
 * <ul>
 *   <li>Fleet owners can only access resources where {@code fleet_owner_id = caller.id}.</li>
 *   <li>Admin access is handled at the controller layer via {@code @PreAuthorize}.</li>
 *   <li>IDOR protection: every ownership check is server-side; no client-supplied IDs are trusted.</li>
 * </ul>
 *
 * <p>KYC rule (M8A): driver activation status is NOT bypassed here.
 *   A driver's {@code isActive} flag remains controlled by the KYC document approval
 *   flow in {@code DriverDocumentService}. Fleet association does not grant operational status.
 */
@Service
@RequiredArgsConstructor
public class FleetServiceImpl implements FleetService {

    private static final Logger log = LoggerFactory.getLogger(FleetServiceImpl.class);

    // Terminal / "done" statuses — excluded when counting active fleet bookings
    private static final List<Booking.BookingStatus> INACTIVE_STATUSES = Arrays.asList(
            Booking.BookingStatus.COMPLETED,
            Booking.BookingStatus.CANCELLED
    );

    private final UserRepository userRepository;
    private final VehicleRepository vehicleRepository;
    private final BookingRepository bookingRepository;

    // ── validateFleetOwner ────────────────────────────────────────────────────

    @Override
    public User validateFleetOwner(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new FleetException.FleetResourceNotFoundException(
                        "Fleet owner not found: " + userId));
        if (user.getRole() != User.Role.FLEET_OWNER) {
            throw new FleetException.FleetAuthorizationException(
                    "User " + userId + " is not a fleet owner");
        }
        return user;
    }

    // ── getDashboard ──────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public FleetDashboardResponse getDashboard(Long fleetOwnerId, int page, int size) {
        validateFleetOwner(fleetOwnerId);

        List<User> drivers = userRepository.findByFleetOwnerId(fleetOwnerId);
        List<Vehicle> vehicles = vehicleRepository.findByFleetOwnerId(fleetOwnerId);

        // Build set of driver IDs for booking lookup
        Set<Long> driverIds = drivers.stream()
                .map(User::getId)
                .collect(Collectors.toSet());

        long activeBookingCount = driverIds.isEmpty()
                ? 0
                : bookingRepository.countActiveByDriverIdIn(driverIds, INACTIVE_STATUSES);
        Slice<BookingRepository.FleetActiveBookingProjection> activeBookingPage = driverIds.isEmpty()
                ? new org.springframework.data.domain.SliceImpl<>(
                        List.of(), PageRequest.of(page, size), false)
                : bookingRepository.findActiveDashboardBookings(
                        driverIds, INACTIVE_STATUSES, PageRequest.of(page, size));

        List<FleetDashboardResponse.DriverSummary> driverSummaries = drivers.stream()
                .map(d -> FleetDashboardResponse.DriverSummary.builder()
                        .id(d.getId())
                        .fullName(d.getFullName())
                        .phone(d.getPhone())
                        .isActive(d.getIsActive())
                        .verificationStatus(d.getVerificationStatus())
                        .build())
                .toList();

        List<FleetDashboardResponse.VehicleSummary> vehicleSummaries = vehicles.stream()
                .map(v -> FleetDashboardResponse.VehicleSummary.builder()
                        .id(v.getId())
                        .plateNumber(v.getPlateNumber())
                        .truckType(v.getTruckType())
                        .capacity(v.getCapacity())
                        .availabilityStatus(v.getAvailabilityStatus())
                        .assignedDriverId(v.getUserId())
                        .build())
                .toList();

        List<FleetDashboardResponse.FleetBookingSummary> bookingSummaries = activeBookingPage.getContent().stream()
                .map(b -> FleetDashboardResponse.FleetBookingSummary.builder()
                        .bookingId(b.getBookingId())
                        .driverId(b.getDriverId())
                        .driverName(b.getDriverName() != null ? b.getDriverName() : "Unknown")
                        .status(b.getStatus() != null ? b.getStatus().name() : null)
                        .route(buildRoute(b.getPickupLocation(), b.getDestination()))
                        .build())
                .toList();

        log.info("Fleet dashboard: owner={}, drivers={}, vehicles={}, activeBookings={}",
                fleetOwnerId, drivers.size(), vehicles.size(), activeBookingCount);

        return FleetDashboardResponse.builder()
                .fleetOwnerId(fleetOwnerId)
                .driverCount(drivers.size())
                .vehicleCount(vehicles.size())
                .activeBookingCount(Math.toIntExact(activeBookingCount))
                .drivers(driverSummaries)
                .vehicles(vehicleSummaries)
                .activeBookings(bookingSummaries)
                .page(page)
                .pageSize(size)
                .hasNext(activeBookingPage.hasNext())
                .build();
    }

    // ── getFleetDrivers ───────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<FleetDriverResponse> getFleetDrivers(Long fleetOwnerId) {
        validateFleetOwner(fleetOwnerId);
        return userRepository.findByFleetOwnerId(fleetOwnerId)
                .stream()
                .map(FleetDriverResponse::from)
                .toList();
    }

    // ── getFleetVehicles ──────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<FleetVehicleResponse> getFleetVehicles(Long fleetOwnerId) {
        validateFleetOwner(fleetOwnerId);
        return vehicleRepository.findByFleetOwnerId(fleetOwnerId)
                .stream()
                .map(FleetVehicleResponse::from)
                .toList();
    }

    // ── addDriver ─────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public FleetDriverResponse addDriver(Long fleetOwnerId, AddDriverRequest request) {
        validateFleetOwner(fleetOwnerId);

        User driver = userRepository.findByIdForUpdate(request.getDriverId())
                .orElseThrow(() -> new FleetException.FleetResourceNotFoundException(
                        "Driver not found: " + request.getDriverId()));

        // Must be a driver role
        if (driver.getRole() != User.Role.DRIVER) {
            throw new FleetException.FleetValidationException(
                    "User " + request.getDriverId() + " is not a driver (role: " + driver.getRole() + ")");
        }

        // IDOR / cross-fleet protection: cannot poach a driver from another fleet
        if (driver.getFleetOwnerId() != null && !driver.getFleetOwnerId().equals(fleetOwnerId)) {
            throw new FleetException.ResourceAlreadyAssignedException(
                    "Driver " + request.getDriverId() + " already belongs to another fleet");
        }

        // Idempotent: if already in this fleet, return current state
        if (fleetOwnerId.equals(driver.getFleetOwnerId())) {
            log.info("addDriver: driver {} is already in fleet {}", driver.getId(), fleetOwnerId);
            return FleetDriverResponse.from(driver);
        }

        driver.setFleetOwnerId(fleetOwnerId);
        driver = userRepository.save(driver);

        log.info("addDriver: driver {} added to fleet {}", driver.getId(), fleetOwnerId);
        return FleetDriverResponse.from(driver);
    }

    // ── removeDriver ──────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void removeDriver(Long fleetOwnerId, Long driverId) {
        validateFleetOwner(fleetOwnerId);

        User driver = userRepository.findByIdForUpdate(driverId)
                .orElseThrow(() -> new FleetException.FleetResourceNotFoundException(
                        "Driver not found: " + driverId));

        // Ownership check — prevents IDOR on removeDriver
        if (!fleetOwnerId.equals(driver.getFleetOwnerId())) {
            throw new FleetException.FleetAuthorizationException(
                    "Driver " + driverId + " does not belong to your fleet");
        }

        driver.setFleetOwnerId(null);
        userRepository.save(driver);

        log.info("removeDriver: driver {} removed from fleet {}", driverId, fleetOwnerId);
    }

    // ── addVehicle ────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public FleetVehicleResponse addVehicle(Long fleetOwnerId, AddVehicleRequest request) {
        validateFleetOwner(fleetOwnerId);

        // Plate number uniqueness check (consistent with VehicleServiceImpl)
        if (vehicleRepository.existsByPlateNumber(request.getPlateNumber())) {
            throw new FleetException.FleetValidationException(
                    "Plate number already taken: " + request.getPlateNumber());
        }

        // If a driverId is provided, validate the driver belongs to this fleet
        Long assignedUserId = fleetOwnerId; // Default: fleet owner as placeholder
        if (request.getDriverId() != null) {
            User driver = userRepository.findById(request.getDriverId())
                    .orElseThrow(() -> new FleetException.FleetResourceNotFoundException(
                            "Driver not found: " + request.getDriverId()));
            if (!fleetOwnerId.equals(driver.getFleetOwnerId())) {
                throw new FleetException.FleetAuthorizationException(
                        "Driver " + request.getDriverId() + " does not belong to your fleet");
            }
            if (!Boolean.TRUE.equals(driver.getIsActive())) {
                throw new FleetException.FleetValidationException(
                        "Driver " + request.getDriverId() + " is not active. " +
                        "KYC approval is required before a driver can be assigned to a vehicle.");
            }
            assignedUserId = driver.getId();
        }

        // Resolve vehicle category
        Vehicle.VehicleCategory category = null;
        if (request.getVehicleCategory() != null) {
            try {
                category = Vehicle.VehicleCategory.valueOf(request.getVehicleCategory().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new FleetException.FleetValidationException(
                        "Invalid vehicle category: " + request.getVehicleCategory() + ". Must be 'heavy' or 'light'.");
            }
        }

        Vehicle vehicle = Vehicle.builder()
                .userId(assignedUserId)
                .fleetOwnerId(fleetOwnerId)
                .truckType(request.getTruckType())
                .plateNumber(request.getPlateNumber())
                .capacity(request.getCapacity())
                .currentCity(request.getCurrentCity())
                .vehicleCategory(category)
                .availabilityStatus("available")
                .build();

        vehicle = vehicleRepository.save(vehicle);

        log.info("addVehicle: vehicle {} (plate={}) created for fleet {}",
                vehicle.getId(), vehicle.getPlateNumber(), fleetOwnerId);

        return FleetVehicleResponse.from(vehicle);
    }

    // ── assignVehicle ─────────────────────────────────────────────────────────

    @Override
    @Transactional
    public FleetVehicleResponse assignVehicle(Long fleetOwnerId, Long vehicleId, AssignVehicleRequest request) {
        validateFleetOwner(fleetOwnerId);

        // Ownership check on vehicle — IDOR protection
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new FleetException.FleetResourceNotFoundException(
                        "Vehicle not found: " + vehicleId));
        if (!fleetOwnerId.equals(vehicle.getFleetOwnerId())) {
            throw new FleetException.FleetAuthorizationException(
                    "Vehicle " + vehicleId + " does not belong to your fleet");
        }

        // Ownership check on driver — IDOR protection
        User driver = userRepository.findById(request.getDriverId())
                .orElseThrow(() -> new FleetException.FleetResourceNotFoundException(
                        "Driver not found: " + request.getDriverId()));
        if (!fleetOwnerId.equals(driver.getFleetOwnerId())) {
            throw new FleetException.FleetAuthorizationException(
                    "Driver " + request.getDriverId() + " does not belong to your fleet");
        }

        // KYC rule (M8A): only active (KYC-approved) drivers may be assigned to vehicles
        if (Boolean.FALSE.equals(driver.getIsActive())) {
            throw new FleetException.FleetValidationException(
                    "Driver " + request.getDriverId() + " is not active. " +
                    "KYC approval is required before a driver can be assigned to a vehicle.");
        }

        vehicle.setUserId(driver.getId());
        vehicle = vehicleRepository.save(vehicle);

        log.info("assignVehicle: vehicle {} assigned to driver {} in fleet {}",
                vehicleId, driver.getId(), fleetOwnerId);

        return FleetVehicleResponse.from(vehicle);
    }

    // ── getAllFleetOwners (Admin) ─────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<AdminFleetOwnerResponse> getAllFleetOwners(User adminUser) {
        if (adminUser == null || adminUser.getRole() != User.Role.ADMIN) {
            throw new FleetException.FleetAuthorizationException(
                    "Admin authorization required to view all fleet owners");
        }

        List<User> fleetOwners = userRepository.findByRoleOrderByCreatedAtDesc(User.Role.FLEET_OWNER);
        if (fleetOwners.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> ownerIds = fleetOwners.stream()
                .map(User::getId)
                .collect(Collectors.toList());

        // Batch query for drivers belonging to all retrieved fleet owners (no N+1)
        List<User> allDrivers = userRepository.findByFleetOwnerIdIn(ownerIds);
        Map<Long, List<User>> driversByOwner = allDrivers.stream()
                .filter(d -> d.getFleetOwnerId() != null)
                .collect(Collectors.groupingBy(User::getFleetOwnerId));

        // Batch query for vehicles belonging to all retrieved fleet owners (no N+1)
        List<Vehicle> allVehicles = vehicleRepository.findByFleetOwnerIdIn(ownerIds);
        Map<Long, List<Vehicle>> vehiclesByOwner = allVehicles.stream()
                .filter(v -> v.getFleetOwnerId() != null)
                .collect(Collectors.groupingBy(Vehicle::getFleetOwnerId));

        return fleetOwners.stream().map(owner -> {
            List<User> drivers = driversByOwner.getOrDefault(owner.getId(), Collections.emptyList());
            List<Vehicle> vehicles = vehiclesByOwner.getOrDefault(owner.getId(), Collections.emptyList());

            List<AdminFleetDriverSummary> driverSummaries = drivers.stream()
                    .map(d -> AdminFleetDriverSummary.builder()
                            .id(d.getId())
                            .name(d.getFullName())
                            .phone(d.getPhone())
                            .verified(Boolean.TRUE.equals(d.getVerificationStatus()))
                            .build())
                    .collect(Collectors.toList());

            List<AdminFleetVehicleSummary> vehicleSummaries = vehicles.stream()
                    .map(v -> AdminFleetVehicleSummary.builder()
                            .id(v.getId())
                            .plateNumber(v.getPlateNumber())
                            .truckType(v.getTruckType())
                            .status(v.getAvailabilityStatus())
                            .build())
                    .collect(Collectors.toList());

            return AdminFleetOwnerResponse.builder()
                    .id(owner.getId())
                    .name(owner.getFullName())
                    .email(owner.getEmail())
                    .phone(owner.getPhone())
                    .role(owner.getRole() != null ? owner.getRole().name().toLowerCase() : "fleet_owner")
                    .isVerified(Boolean.TRUE.equals(owner.getVerificationStatus()))
                    .isActive(owner.getIsActive() != null ? owner.getIsActive() : true)
                    .driverCount(drivers.size())
                    .vehicleCount(vehicles.size())
                    .drivers(driverSummaries)
                    .vehicles(vehicleSummaries)
                    .createdAt(owner.getCreatedAt())
                    .updatedAt(owner.getUpdatedAt())
                    .build();
        }).collect(Collectors.toList());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String buildRoute(String pickup, String dest) {
        if (pickup == null && dest == null) return "N/A";
        return (pickup != null ? pickup : "?") + " → " + (dest != null ? dest : "?");
    }
}
