package com.ethioloadai.vehicle.service;

import com.ethioloadai.exception.AuthorizationException;
import com.ethioloadai.exception.ValidationException;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.user.repository.UserRepository;
import com.ethioloadai.vehicle.dto.*;
import com.ethioloadai.vehicle.entity.Vehicle;
import com.ethioloadai.vehicle.mapper.VehicleMapper;
import com.ethioloadai.vehicle.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class VehicleServiceImpl implements VehicleService {
    private static final Logger log = LoggerFactory.getLogger(VehicleServiceImpl.class);

    private final VehicleRepository vehicleRepository;
    private final UserRepository userRepository;
    private final VehicleMapper vehicleMapper;

        private static final Map<Vehicle.VehicleApprovalStatus, EnumSet<Vehicle.VehicleApprovalStatus>> APPROVAL_TRANSITIONS = new EnumMap<>(Map.of(
            Vehicle.VehicleApprovalStatus.UNDER_REVIEW, EnumSet.of(Vehicle.VehicleApprovalStatus.APPROVED, Vehicle.VehicleApprovalStatus.REJECTED),
            Vehicle.VehicleApprovalStatus.APPROVED, EnumSet.of(Vehicle.VehicleApprovalStatus.SUSPENDED),
            Vehicle.VehicleApprovalStatus.SUSPENDED, EnumSet.of(Vehicle.VehicleApprovalStatus.APPROVED),
            Vehicle.VehicleApprovalStatus.REJECTED, EnumSet.of(Vehicle.VehicleApprovalStatus.UNDER_REVIEW)
        ));

    @Override
    @Transactional
    public VehicleResponse createVehicle(VehicleCreateRequest request, Long userId) {
        log.info("Creating vehicle for user ID: {}", userId);

        // Verify user exists
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthorizationException.ResourceNotFoundException("User"));

        // Check plate number uniqueness
        if (vehicleRepository.existsByPlateNumber(request.getPlateNumber())) {
            log.warn("Vehicle creation failed - plate number already exists: {}", request.getPlateNumber());
            throw new ValidationException("The plate number has already been taken.", "plate_number", "The plate number has already been taken.");
        }

        // Map request to entity
        Vehicle vehicle = vehicleMapper.toEntity(request);
        vehicle.setUserId(userId);
        vehicle.setApprovalStatus(Vehicle.VehicleApprovalStatus.UNDER_REVIEW);

        // Set fleet owner if user is a fleet owner
        if (user.getRole() == User.Role.FLEET_OWNER) {
            vehicle.setFleetOwnerId(userId);
        }

        // Save vehicle
        vehicle = vehicleRepository.save(vehicle);

        log.info("Vehicle created successfully - vehicleId: {}, userId: {}", vehicle.getId(), userId);

        return vehicleMapper.toResponse(vehicle);
    }

    @Override
    @Transactional(readOnly = true)
    public VehicleResponse getVehicleById(Long id, Long userId) {
        log.info("Fetching vehicle with ID: {} for user: {}", id, userId);

        Vehicle vehicle = vehicleRepository.findById(id)
                .orElseThrow(() -> new AuthorizationException.ResourceNotFoundException("Vehicle"));

        // Check ownership or fleet ownership
        if (!vehicle.getUserId().equals(userId) && 
            (vehicle.getFleetOwnerId() == null || !vehicle.getFleetOwnerId().equals(userId))) {
            log.warn("Access denied - user {} does not own vehicle {}", userId, id);
            throw new AuthorizationException.AccessDeniedException();
        }

        return vehicleMapper.toResponse(vehicle);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VehicleResponse> getMyVehicles(Long userId) {
        log.info("Fetching vehicles for user ID: {}", userId);
        List<Vehicle> vehicles = vehicleRepository.findByUserId(userId);
        return vehicles.stream()
                .map(vehicleMapper::toResponse)
                .toList();
    }

    /**
     * Returns all vehicles on the platform. ADMIN access only.
     *
     * <p>Callers must pass their own userId so this method can verify the role.
     * Any non-admin call is rejected with {@link AuthorizationException.AccessDeniedException}.
     */
    @Override
    @Transactional(readOnly = true)
    public List<VehicleResponse> getAllVehicles(Long requestingUserId) {
        log.info("Admin vehicle listing requested by userId: {}", requestingUserId);

        User requestingUser = userRepository.findById(requestingUserId)
                .orElseThrow(() -> new AuthorizationException.ResourceNotFoundException("User"));

        if (requestingUser.getRole() != User.Role.ADMIN) {
            log.warn("getAllVehicles rejected — userId {} is not an admin", requestingUserId);
            throw new AuthorizationException.AccessDeniedException();
        }

        List<Vehicle> vehicles = vehicleRepository.findAll();
        return vehicles.stream()
                .map(vehicleMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public VehicleResponse updateApprovalStatus(Long id, VehicleApprovalStatusRequest request, Long requestingUserId) {
        User admin = userRepository.findById(requestingUserId)
                .orElseThrow(() -> new AuthorizationException.ResourceNotFoundException("User"));
        if (admin.getRole() != User.Role.ADMIN) {
            throw new AuthorizationException.AccessDeniedException();
        }

        Vehicle vehicle = vehicleRepository.findById(id)
                .orElseThrow(() -> new AuthorizationException.ResourceNotFoundException("Vehicle"));
        Vehicle.VehicleApprovalStatus current = vehicle.getApprovalStatus();
        Vehicle.VehicleApprovalStatus target = Vehicle.VehicleApprovalStatus.valueOf(request.getStatus().name());
        if (current == target || !APPROVAL_TRANSITIONS.getOrDefault(current, EnumSet.noneOf(Vehicle.VehicleApprovalStatus.class)).contains(target)) {
            throw new ValidationException("Invalid vehicle approval transition: " + current + " -> " + target, "approval_status", "Invalid vehicle approval transition");
        }

        vehicle.setApprovalStatus(target);
        log.info("Vehicle approval status updated — vehicleId: {}, adminId: {}, from: {}, to: {}", id, requestingUserId, current, target);
        return vehicleMapper.toResponse(vehicleRepository.save(vehicle));
    }

    @Override
    @Transactional
    public VehicleResponse updateVehicle(Long id, VehicleUpdateRequest request, Long userId) {
        log.info("Updating vehicle with ID: {} for user: {}", id, userId);

        Vehicle vehicle = vehicleRepository.findById(id)
                .orElseThrow(() -> new AuthorizationException.ResourceNotFoundException("Vehicle"));

        // Check ownership or fleet ownership
        if (!vehicle.getUserId().equals(userId) && 
            (vehicle.getFleetOwnerId() == null || !vehicle.getFleetOwnerId().equals(userId))) {
            log.warn("Access denied - user {} does not own vehicle {}", userId, id);
            throw new AuthorizationException.AccessDeniedException();
        }

        // Check plate number uniqueness if changed
        if (request.getPlateNumber() != null && !request.getPlateNumber().equals(vehicle.getPlateNumber())) {
            if (vehicleRepository.existsByPlateNumberAndIdNot(request.getPlateNumber(), id)) {
                throw new ValidationException("The plate number has already been taken.", "plate_number", "The plate number has already been taken.");
            }
        }

        // Map request to entity
        vehicleMapper.updateEntityFromDto(request, vehicle);

        // Save vehicle
        vehicle = vehicleRepository.save(vehicle);

        log.info("Vehicle updated successfully - vehicleId: {}", id);

        return vehicleMapper.toResponse(vehicle);
    }

    @Override
    @Transactional
    public void deleteVehicle(Long id, Long userId) {
        log.info("Deleting vehicle with ID: {} for user: {}", id, userId);

        Vehicle vehicle = vehicleRepository.findById(id)
                .orElseThrow(() -> new AuthorizationException.ResourceNotFoundException("Vehicle"));

        // Check ownership or fleet ownership
        if (!vehicle.getUserId().equals(userId) && 
            (vehicle.getFleetOwnerId() == null || !vehicle.getFleetOwnerId().equals(userId))) {
            log.warn("Access denied - user {} does not own vehicle {}", userId, id);
            throw new AuthorizationException.AccessDeniedException();
        }

        vehicleRepository.delete(vehicle);

        log.info("Vehicle deleted successfully - vehicleId: {}", id);
    }

    @Override
    @Transactional
    public VehicleResponse updateVehicleLocation(Long id, VehicleLocationRequest request, Long userId) {
        log.info("Updating location for vehicle ID: {} for user: {}", id, userId);

        Vehicle vehicle = vehicleRepository.findById(id)
                .orElseThrow(() -> new AuthorizationException.ResourceNotFoundException("Vehicle"));

        // Check ownership
        if (!vehicle.getUserId().equals(userId)) {
            log.warn("Access denied - user {} does not own vehicle {}", userId, id);
            throw new AuthorizationException.AccessDeniedException();
        }

        // Update location
        vehicleMapper.updateLocationFromDto(request, vehicle);

        // Save vehicle
        vehicle = vehicleRepository.save(vehicle);

        log.info("Vehicle location updated successfully - vehicleId: {}", id);

        return vehicleMapper.toResponse(vehicle);
    }

    @Override
    @Transactional
    public VehicleResponse updateDriverLocation(VehicleLocationRequest request, Long userId) {
        log.info("Updating driver location for user ID: {}", userId);

        List<Vehicle> vehicles = vehicleRepository.findByUserId(userId);
        if (vehicles.isEmpty()) {
            log.warn("updateDriverLocation failed — no vehicle found for userId: {}", userId);
            throw new AuthorizationException.ResourceNotFoundException("Vehicle");
        }

        // Select the vehicle that was most recently located (highest lastLocationAt).
        // If no vehicle has ever been located, fall back to the most recently created
        // (highest id). This avoids the arbitrary get(0) ordering that depends on
        // the database insertion order rather than any meaningful business rule.
        Vehicle vehicle = vehicles.stream()
                .max(Comparator.comparing(
                        v -> v.getLastLocationAt() != null
                                ? v.getLastLocationAt()
                                : LocalDateTime.MIN,
                        Comparator.naturalOrder()
                ))
                .orElseThrow(() -> new AuthorizationException.ResourceNotFoundException("Vehicle"));

        // Update location
        vehicleMapper.updateLocationFromDto(request, vehicle);

        // Save vehicle
        vehicle = vehicleRepository.save(vehicle);

        log.info("Driver location updated — vehicleId: {}, userId: {}", vehicle.getId(), userId);

        return vehicleMapper.toResponse(vehicle);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VehicleResponse> getNearbyVehicles(BigDecimal latitude, BigDecimal longitude, Double radius) {
        log.info("Fetching nearby vehicles - lat: {}, lng: {}, radius: {}km", latitude, longitude, radius);
        List<Vehicle> vehicles = vehicleRepository.findNearbyAvailableVehicles(latitude, longitude, radius);
        return vehicles.stream()
                .map(vehicleMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<VehicleResponse> getAvailableVehiclesInCity(String city) {
        log.info("Fetching available vehicles in city: {}", city);
        List<Vehicle> vehicles = vehicleRepository.findAvailableVehiclesInCity(city);
        return vehicles.stream()
                .map(vehicleMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public VehicleResponse updateCurrentCity(Long id, String currentCity, Long userId) {
        log.info("Updating current city for vehicle ID: {} to: {}", id, currentCity);

        Vehicle vehicle = vehicleRepository.findById(id)
                .orElseThrow(() -> new AuthorizationException.ResourceNotFoundException("Vehicle"));

        // Check ownership
        if (!vehicle.getUserId().equals(userId)) {
            log.warn("Access denied - user {} does not own vehicle {}", userId, id);
            throw new AuthorizationException.AccessDeniedException();
        }

        // Update current city
        vehicle.setCurrentCity(currentCity);

        // Save vehicle
        vehicle = vehicleRepository.save(vehicle);

        log.info("Vehicle current city updated successfully - vehicleId: {}", id);

        return vehicleMapper.toResponse(vehicle);
    }
}
