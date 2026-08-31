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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class VehicleServiceImpl implements VehicleService {

    private final VehicleRepository vehicleRepository;
    private final UserRepository userRepository;
    private final VehicleMapper vehicleMapper;

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

    @Override
    @Transactional(readOnly = true)
    public List<VehicleResponse> getAllVehicles() {
        log.info("Fetching all vehicles");
        List<Vehicle> vehicles = vehicleRepository.findAll();
        return vehicles.stream()
                .map(vehicleMapper::toResponse)
                .toList();
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

        // Get user's primary vehicle (first vehicle)
        List<Vehicle> vehicles = vehicleRepository.findByUserId(userId);
        if (vehicles.isEmpty()) {
            log.warn("No vehicle found for user: {}", userId);
            throw new AuthorizationException.ResourceNotFoundException("Vehicle");
        }

        Vehicle vehicle = vehicles.get(0);

        // Update location
        vehicleMapper.updateLocationFromDto(request, vehicle);

        // Save vehicle
        vehicle = vehicleRepository.save(vehicle);

        log.info("Driver location updated successfully - vehicleId: {}, userId: {}", vehicle.getId(), userId);

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
