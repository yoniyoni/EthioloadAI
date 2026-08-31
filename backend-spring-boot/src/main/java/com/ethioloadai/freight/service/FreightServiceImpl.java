package com.ethioloadai.freight.service;

import com.ethioloadai.exception.AuthorizationException;
import com.ethioloadai.exception.ValidationException;
import com.ethioloadai.freight.dto.*;
import com.ethioloadai.freight.entity.Freight;
import com.ethioloadai.freight.mapper.FreightMapper;
import com.ethioloadai.freight.repository.FreightRepository;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FreightServiceImpl implements FreightService {

    private final FreightRepository freightRepository;
    private final UserRepository userRepository;
    private final FreightMapper freightMapper;

    @Override
    @Transactional
    public FreightResponse createFreight(Long userId, CreateFreightRequest request) {
        log.info("Creating freight for user ID: {}", userId);

        // Verify user exists and has appropriate role
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthorizationException.ResourceNotFoundException("User"));

        if (user.getRole() != User.Role.SHIPPER && user.getRole() != User.Role.ADMIN) {
            log.warn("Freight creation failed - user {} is not a shipper or admin", userId);
            throw new AuthorizationException("Only shippers and admins can create freight");
        }

        // Calculate distance if coordinates provided
        if (request.getPickupLatitude() != null && request.getPickupLongitude() != null &&
            request.getDestinationLatitude() != null && request.getDestinationLongitude() != null) {
            BigDecimal distance = calculateDistance(
                request.getPickupLatitude(),
                request.getPickupLongitude(),
                request.getDestinationLatitude(),
                request.getDestinationLongitude()
            );
            // Update the request with calculated distance
            CreateFreightRequest updatedRequest = CreateFreightRequest.builder()
                    .pickupLocation(request.getPickupLocation())
                    .pickupLatitude(request.getPickupLatitude())
                    .pickupLongitude(request.getPickupLongitude())
                    .destination(request.getDestination())
                    .destinationLatitude(request.getDestinationLatitude())
                    .destinationLongitude(request.getDestinationLongitude())
                    .materialType(request.getMaterialType())
                    .cargoDescription(request.getCargoDescription())
                    .weightTons(request.getWeightTons())
                    .volumeM3(request.getVolumeM3())
                    .budget(request.getBudget())
                    .distanceKm(distance)
                    .deadline(request.getDeadline())
                    .urgencyLevel(request.getUrgencyLevel())
                    .build();
            
            Freight freight = freightMapper.toEntity(updatedRequest);
            freight.setShipper(user);
            freight.setStatus(Freight.FreightStatus.POSTED);
            freight = freightRepository.save(freight);
            
            log.info("Freight created successfully - freightId: {}, userId: {}, distance: {}km", 
                    freight.getId(), userId, distance);
            
            return freightMapper.toResponse(freight);
        }

        // Map request to entity
        Freight freight = freightMapper.toEntity(request);
        freight.setShipper(user);
        freight.setStatus(Freight.FreightStatus.POSTED);

        // Save freight
        freight = freightRepository.save(freight);

        log.info("Freight created successfully - freightId: {}, userId: {}", freight.getId(), userId);

        return freightMapper.toResponse(freight);
    }

    @Override
    @Transactional(readOnly = true)
    public FreightResponse getFreightById(Long id) {
        log.info("Fetching freight with ID: {}", id);

        Freight freight = freightRepository.findById(id)
                .orElseThrow(() -> new AuthorizationException.ResourceNotFoundException("Freight"));

        return freightMapper.toResponse(freight);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FreightResponse> getUserFreights(Long userId) {
        log.info("Fetching freight for user ID: {}", userId);

        // Verify user exists
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthorizationException.ResourceNotFoundException("User"));

        if (user.getRole() != User.Role.SHIPPER && user.getRole() != User.Role.ADMIN) {
            log.warn("Freight listing failed - user {} is not a shipper or admin", userId);
            throw new AuthorizationException("Only shippers and admins can view freight");
        }

        List<Freight> freights = freightRepository.findByShipperId(userId);
        return freights.stream()
                .map(freightMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<FreightResponse> searchFreights(FreightFilterRequest filter) {
        log.info("Searching freight with filters: {}", filter);

        Specification<Freight> spec = buildSpecification(filter);
        List<Freight> freights = freightRepository.findAll(spec);

        return freights.stream()
                .map(freightMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public FreightResponse updateFreight(Long id, Long userId, UpdateFreightRequest request) {
        log.info("Updating freight with ID: {} for user: {}", id, userId);

        Freight freight = freightRepository.findById(id)
                .orElseThrow(() -> new AuthorizationException.ResourceNotFoundException("Freight"));

        // Check ownership or admin
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthorizationException.ResourceNotFoundException("User"));

        if (!freight.getShipper().getId().equals(userId) && user.getRole() != User.Role.ADMIN) {
            log.warn("Access denied - user {} does not own freight {}", userId, id);
            throw new AuthorizationException.OwnershipException();
        }

        // Validate status transition if status is being updated
        if (request.getStatus() != null && !isValidStatusTransition(freight.getStatus(), request.getStatus())) {
            throw new ValidationException("Invalid status transition from " + freight.getStatus() + " to " + request.getStatus(),
                    "status", "Cannot transition from " + freight.getStatus() + " to " + request.getStatus());
        }

        // Map request to entity
        freightMapper.updateEntityFromDto(request, freight);

        // Save freight
        freight = freightRepository.save(freight);

        log.info("Freight updated successfully - freightId: {}", id);

        return freightMapper.toResponse(freight);
    }

    @Override
    @Transactional
    public void cancelFreight(Long id, Long userId) {
        log.info("Cancelling freight with ID: {} for user: {}", id, userId);

        Freight freight = freightRepository.findById(id)
                .orElseThrow(() -> new AuthorizationException.ResourceNotFoundException("Freight"));

        // Check ownership or admin
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthorizationException.ResourceNotFoundException("User"));

        if (!freight.getShipper().getId().equals(userId) && user.getRole() != User.Role.ADMIN) {
            log.warn("Access denied - user {} does not own freight {}", userId, id);
            throw new AuthorizationException.OwnershipException();
        }

        // Validate that freight can be cancelled (only POSTED status)
        if (freight.getStatus() != Freight.FreightStatus.POSTED) {
            throw new ValidationException("Can only cancel freight in POSTED status",
                    "status", "Freight cannot be cancelled in current status: " + freight.getStatus());
        }

        freight.setStatus(Freight.FreightStatus.CANCELLED);
        freightRepository.save(freight);

        log.info("Freight cancelled successfully - freightId: {}", id);
    }

    private Specification<Freight> buildSpecification(FreightFilterRequest filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), Freight.FreightStatus.valueOf(filter.getStatus().toUpperCase())));
            }

            if (filter.getMaterialType() != null) {
                predicates.add(cb.equal(root.get("materialType"), filter.getMaterialType()));
            }

            if (filter.getPickupLocation() != null) {
                predicates.add(cb.like(cb.lower(root.get("pickupLocation")), 
                        "%" + filter.getPickupLocation().toLowerCase() + "%"));
            }

            if (filter.getDestination() != null) {
                predicates.add(cb.like(cb.lower(root.get("destination")), 
                        "%" + filter.getDestination().toLowerCase() + "%"));
            }

            if (filter.getMinWeight() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("weightTons"), filter.getMinWeight()));
            }

            if (filter.getMaxWeight() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("weightTons"), filter.getMaxWeight()));
            }

            if (filter.getMinBudget() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("budget"), filter.getMinBudget()));
            }

            if (filter.getMaxBudget() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("budget"), filter.getMaxBudget()));
            }

            if (filter.getDeadlineAfter() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("deadline"), filter.getDeadlineAfter()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private boolean isValidStatusTransition(Freight.FreightStatus current, Freight.FreightStatus target) {
        // Define valid transitions
        return switch (current) {
            case POSTED -> target == Freight.FreightStatus.MATCHED || target == Freight.FreightStatus.CANCELLED;
            case MATCHED -> target == Freight.FreightStatus.IN_TRANSIT || target == Freight.FreightStatus.CANCELLED;
            case IN_TRANSIT -> target == Freight.FreightStatus.DELIVERED;
            case DELIVERED -> target == Freight.FreightStatus.COMPLETED;
            case COMPLETED -> false; // Terminal state
            case CANCELLED -> false; // Terminal state
        };
    }

    private BigDecimal calculateDistance(BigDecimal lat1, BigDecimal lon1, BigDecimal lat2, BigDecimal lon2) {
        // Haversine formula to calculate distance between two points on Earth
        final int R = 6371; // Earth's radius in kilometers

        double lat1Rad = Math.toRadians(lat1.doubleValue());
        double lat2Rad = Math.toRadians(lat2.doubleValue());
        double deltaLat = Math.toRadians(lat2.subtract(lat1).doubleValue());
        double deltaLon = Math.toRadians(lon2.subtract(lon1).doubleValue());

        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
                   Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                   Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return BigDecimal.valueOf(R * c).setScale(2, RoundingMode.HALF_UP);
    }
}
