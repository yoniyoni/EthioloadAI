package com.ethioloadai.trip.service;

import com.ethioloadai.freight.entity.Freight;
import com.ethioloadai.freight.repository.FreightRepository;
import com.ethioloadai.trip.entity.Trip;
import com.ethioloadai.trip.entity.TripStop;
import com.ethioloadai.trip.exception.TripNotFoundException;
import com.ethioloadai.trip.exception.TripStopNotFoundException;
import com.ethioloadai.trip.exception.TripStopValidationException;
import com.ethioloadai.trip.exception.TripValidationException;
import com.ethioloadai.trip.repository.TripRepository;
import com.ethioloadai.trip.repository.TripStopRepository;
import com.ethioloadai.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TripStopServiceImpl implements TripStopService {
    private static final Logger log = LoggerFactory.getLogger(TripStopServiceImpl.class);

    private final TripStopRepository tripStopRepository;
    private final TripRepository tripRepository;
    private final FreightRepository freightRepository;
    private final TripInvariantService tripInvariantService;
    private final MultiStopBusinessRulesService multiStopBusinessRulesService;

    // Business rule constants
    private static final BigDecimal MIN_LATITUDE = new BigDecimal("-90");
    private static final BigDecimal MAX_LATITUDE = new BigDecimal("90");
    private static final BigDecimal MIN_LONGITUDE = new BigDecimal("-180");
    private static final BigDecimal MAX_LONGITUDE = new BigDecimal("180");

    @Override
    @Transactional
    public TripStop addStop(Long tripId, String locationName, Integer stopOrder,
                           BigDecimal pickupLat, BigDecimal pickupLng, BigDecimal agreedPrice,
                           Long cargoRequestId, String notes, User user) {
        
        log.info("Adding stop to trip {} by user {}: location={}, order={}", 
                tripId, user.getId(), locationName, stopOrder);

        // Get trip with authorization
        Trip trip = getTripWithAuthorization(tripId, user);
        
        // Validate stop addition eligibility
        validateStopAddition(tripId, stopOrder, user);
        
        // Auto-assign stop order if not provided
        if (stopOrder == null) {
            stopOrder = multiStopBusinessRulesService.getNextStopOrder(trip);
        }
        
        // Validate stop addition using business rules
        MultiStopBusinessRulesService.StopAdditionValidation validation = 
            multiStopBusinessRulesService.validateStopAddition(trip, stopOrder, cargoRequestId, user);
        
        if (!validation.canAdd()) {
            throw new TripStopValidationException("Stop addition validation failed: " + 
                String.join(", ", validation.violations()));
        }
        
        // Validate input data
        validateStopData(locationName, pickupLat, pickupLng, agreedPrice, stopOrder);
        
        // Validate cargo request if provided
        Freight cargoRequest = null;
        if (cargoRequestId != null) {
            cargoRequest = validateAndGetCargoRequest(cargoRequestId, user);
        }
        
        // Check for duplicate stop order
        if (tripStopRepository.findByTrip_IdAndStopOrder(tripId, stopOrder).isPresent()) {
            throw TripStopValidationException.duplicateStopOrder(stopOrder, tripId);
        }

        // Create stop
        TripStop stop = TripStop.builder()
                .trip(trip)
                .cargoRequest(cargoRequest)
                .stopOrder(stopOrder)
                .locationName(locationName)
                .pickupLat(pickupLat)
                .pickupLng(pickupLng)
                .agreedPrice(agreedPrice != null ? agreedPrice : BigDecimal.ZERO)
                .stopStatus(TripStop.StopStatus.PENDING)
                .notes(notes)
                .build();

        TripStop savedStop = tripStopRepository.save(stop);

        // Update trip counters and type using business rules
        updateTripForNewStop(trip);
        multiStopBusinessRulesService.updateTripTypeForStopCount(trip);

        log.info("Stop {} added to trip {} at order {}", savedStop.getId(), tripId, stopOrder);
        return initializeResponseGraph(savedStop);
    }

    @Override
    @Transactional
    public TripStop updateStop(Long stopId, String locationName, 
                              BigDecimal pickupLat, BigDecimal pickupLng,
                              BigDecimal agreedPrice, String notes, User user) {
        
        log.debug("Updating stop {} by user {}", stopId, user.getId());

        TripStop stop = getStopWithAuthorization(stopId, user);
        
        // Validate stop can be modified
        validateStopModification(stop);
        
        // Update fields if provided
        if (locationName != null) {
            validateLocationName(locationName);
            stop.setLocationName(locationName);
        }
        
        if (pickupLat != null || pickupLng != null) {
            validateCoordinates(pickupLat, pickupLng);
            if (pickupLat != null) stop.setPickupLat(pickupLat);
            if (pickupLng != null) stop.setPickupLng(pickupLng);
        }
        
        if (agreedPrice != null) {
            validatePrice(agreedPrice);
            stop.setAgreedPrice(agreedPrice);
        }
        
        if (notes != null) {
            stop.setNotes(notes);
        }

        TripStop updatedStop = tripStopRepository.save(stop);
        log.debug("Stop {} updated successfully", stopId);
        
        return initializeResponseGraph(updatedStop);
    }

    @Override
    @Transactional
    public TripStop updateStopStatus(Long stopId, TripStop.StopStatus newStatus, User user) {
        log.info("Updating stop {} status to {} by user {}", stopId, newStatus, user.getId());

        Long tripId = tripStopRepository.findTripIdById(stopId)
                .orElseThrow(() -> TripStopNotFoundException.withId(stopId));
        Trip trip = tripRepository.findByIdForUpdate(tripId)
                .orElseThrow(() -> TripNotFoundException.withId(tripId));
        TripStop stop = tripStopRepository.findByIdAndTrip_Id(stopId, tripId)
                .orElseThrow(() -> TripStopNotFoundException.withIdAndTrip(stopId, tripId));
        initializeTripOwnershipGraph(trip);
        tripInvariantService.enforceTripOwnership(trip, user);
        
        // Validate user is driver (not shipper)
        validateDriverAccess(trip, user);
        
        // Validate status transition
        validateStopStatusTransition(stop, newStatus);
        
        // Validate status transition using business rules
        MultiStopBusinessRulesService.SequentialCompletionValidation sequentialValidation = 
            multiStopBusinessRulesService.validateSequentialCompletion(stop, newStatus);
        
        if (!sequentialValidation.isValid()) {
            throw TripStopValidationException.invalidSequence(
                String.join(", ", sequentialValidation.violations()));
        }

        if (stop.getStopStatus() == newStatus) {
            return initializeResponseGraph(stop);
        }

        // Update status with appropriate timestamp
        TripStop.StopStatus oldStatus = stop.getStopStatus();
        stop.setStopStatus(newStatus);
        
        switch (newStatus) {
            case ARRIVED:
                stop.setArrivedAt(LocalDateTime.now());
                break;
            case COMPLETED:
                stop.setCompletedAt(LocalDateTime.now());
                break;
        }

        TripStop updatedStop = tripStopRepository.save(stop);
        if (newStatus == TripStop.StopStatus.COMPLETED) {
            updateTripCompletedStops(trip);
        }
        
        log.info("Stop {} status updated from {} to {}", stopId, oldStatus, newStatus);
        return initializeResponseGraph(updatedStop);
    }

    @Override
    @Transactional
    public void removeStop(Long stopId, User user) {
        log.info("Removing stop {} by user {}", stopId, user.getId());

        TripStop stop = getStopWithAuthorization(stopId, user);
        Trip trip = stop.getTrip();
        
        // Validate stop can be removed
        validateStopRemoval(stop);
        
        Integer removedOrder = stop.getStopOrder();
        
        // Remove the stop
        tripStopRepository.delete(stop);
        
        // Reorder remaining stops using business rules
        multiStopBusinessRulesService.resequenceStopsAfterDeletion(trip, removedOrder);
        
        // Update trip counters and type
        updateTripForRemovedStop(trip);
        multiStopBusinessRulesService.updateTripTypeForStopCount(trip);
        
        log.info("Stop {} removed from trip {}", stopId, trip.getId());
    }

    @Override
    @Transactional
    public List<TripStop> reorderStops(Long tripId, List<Long> newOrdering, User user) {
        log.info("Reordering stops for trip {} by user {}: {}", tripId, user.getId(), newOrdering);

        Trip trip = getTripWithAuthorization(tripId, user);
        
        // Validate reordering is allowed
        validateStopReordering(trip);
        
        // Get current stops
        List<TripStop> currentStops = tripStopRepository.findByTrip_IdOrderByStopOrderAsc(tripId);
        
        // Validate new ordering
        validateNewOrdering(currentStops, newOrdering);
        
        // Update stop orders
        for (int i = 0; i < newOrdering.size(); i++) {
            Long stopId = newOrdering.get(i);
            TripStop stop = currentStops.stream()
                    .filter(s -> s.getId().equals(stopId))
                    .findFirst()
                    .orElseThrow(() -> TripStopNotFoundException.withId(stopId));
            
            stop.setStopOrder(i + 1); // 1-based ordering
            tripStopRepository.save(stop);
        }
        
        // Return reordered stops
        List<TripStop> reorderedStops = tripStopRepository.findByTrip_IdOrderByStopOrderAsc(tripId);
        
        log.info("Successfully reordered {} stops for trip {}", reorderedStops.size(), tripId);
        return reorderedStops;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TripStop> getTripStops(Long tripId, User user) {
        Trip trip = getTripWithAuthorization(tripId, user);
        return tripStopRepository.findByTrip_IdOrderByStopOrderAsc(tripId).stream()
            .map(this::initializeResponseGraph)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public TripStop getStopWithAuthorization(Long stopId, User user) {
        TripStop stop = tripStopRepository.findById(stopId)
                .orElseThrow(() -> TripStopNotFoundException.withId(stopId));
        
        // Validate user can access this stop's trip
        initializeTripOwnershipGraph(stop.getTrip());
        tripInvariantService.enforceTripOwnership(stop.getTrip(), user);
        
        return stop;
    }

    private TripStop initializeResponseGraph(TripStop stop) {
        Hibernate.initialize(stop.getTrip());
        Hibernate.initialize(stop.getCargoRequest());
        if (stop.getCargoRequest() != null) {
            Hibernate.initialize(stop.getCargoRequest().getShipper());
        }
        return stop;
    }

    private void initializeTripOwnershipGraph(Trip trip) {
        Hibernate.initialize(trip.getBooking());
        if (trip.getBooking() != null) {
            Hibernate.initialize(trip.getBooking().getDriver());
            Hibernate.initialize(trip.getBooking().getCargoRequest());
            if (trip.getBooking().getCargoRequest() != null) {
                Hibernate.initialize(trip.getBooking().getCargoRequest().getShipper());
            }
        }
    }

    @Override
    @Transactional
    public TripStop arriveAtStop(Long stopId, User user) {
        return updateStopStatus(stopId, TripStop.StopStatus.ARRIVED, user);
    }

    @Override
    @Transactional  
    public TripStop loadAtStop(Long stopId, User user) {
        return updateStopStatus(stopId, TripStop.StopStatus.LOADED, user);
    }

    @Override
    @Transactional
    public TripStop completeStop(Long stopId, User user) {
        return updateStopStatus(stopId, TripStop.StopStatus.COMPLETED, user);
    }

    @Override
    public Integer getNextStopOrder(Long tripId) {
        return tripStopRepository.nextStopOrder(tripId);
    }

    @Override
    public void validateStopAddition(Long tripId, Integer stopOrder, User user) {
        Trip trip = getTripWithAuthorization(tripId, user);
        
        // Use business rules service for comprehensive validation
        MultiStopBusinessRulesService.StopAdditionValidation validation = 
            multiStopBusinessRulesService.validateStopAddition(trip, stopOrder, null, user);
        
        if (!validation.canAdd()) {
            throw new TripStopValidationException("Stop addition validation failed: " + 
                String.join(", ", validation.violations()));
        }
    }

    @Override
    public BigDecimal calculateTotalStopAmount(Long tripId) {
        return tripStopRepository.sumAgreedPriceByTripId(tripId);
    }

    @Override
    public List<TripStop> getStopsRequiringAttention(Long tripId) {
        // Implementation would identify stops that are stuck or overdue
        // For now, return empty list as this is a complex analytical query
        return List.of();
    }

    // ── Private Helper Methods ───────────────────────────────────────────────

    private Trip getTripWithAuthorization(Long tripId, User user) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> TripNotFoundException.withId(tripId));
        
        tripInvariantService.enforceTripOwnership(trip, user);
        return trip;
    }

    private void validateDriverAccess(Trip trip, User user) {
        if (!isAdmin(user) && !trip.getBooking().getDriver().getId().equals(user.getId())) {
            throw TripValidationException.unauthorizedAccess(trip.getId(), user.getId());
        }
    }

    private void validateStopData(String locationName, BigDecimal lat, BigDecimal lng, 
                                 BigDecimal price, Integer order) {
        validateLocationName(locationName);
        validateCoordinates(lat, lng);
        if (price != null) validatePrice(price);
        if (order != null && order < 1) {
            throw TripStopValidationException.invalidStopOrder(order);
        }
    }

    private void validateLocationName(String locationName) {
        if (locationName == null || locationName.trim().isEmpty()) {
            throw TripStopValidationException.locationRequired();
        }
        if (locationName.length() > 255) {
            throw TripStopValidationException.locationRequired();
        }
    }

    private void validateCoordinates(BigDecimal lat, BigDecimal lng) {
        if (lat != null) {
            if (lat.compareTo(MIN_LATITUDE) < 0 || lat.compareTo(MAX_LATITUDE) > 0) {
                throw TripStopValidationException.coordinatesInvalid(
                    "Latitude must be between -90 and 90 degrees");
            }
        }
        
        if (lng != null) {
            if (lng.compareTo(MIN_LONGITUDE) < 0 || lng.compareTo(MAX_LONGITUDE) > 0) {
                throw TripStopValidationException.coordinatesInvalid(
                    "Longitude must be between -180 and 180 degrees");
            }
        }
    }

    private void validatePrice(BigDecimal price) {
        if (price.compareTo(BigDecimal.ZERO) < 0) {
            throw TripStopValidationException.priceValidation("Price cannot be negative");
        }
    }

    private Freight validateAndGetCargoRequest(Long cargoId, User user) {
        Optional<Freight> cargoOpt = freightRepository.findById(cargoId);
        if (cargoOpt.isEmpty()) {
            throw TripStopValidationException.cargoRequestConflict(cargoId, "Cargo request not found");
        }
        
        // Additional validation could check if user has access to this cargo
        return cargoOpt.get();
    }

    private void validateStopModification(TripStop stop) {
        if (stop.getStopStatus() == TripStop.StopStatus.COMPLETED) {
            throw TripStopValidationException.cannotModifyCompleted();
        }
        
        if (stop.getTrip().getTripStatus() != Trip.TripStatus.ONGOING) {
            throw TripStopValidationException.cannotModifyCompleted();
        }
    }

    private void validateStopStatusTransition(TripStop stop, TripStop.StopStatus newStatus) {
        TripStop.StopStatus currentStatus = stop.getStopStatus();
        
        if (!isValidStopStatusTransition(currentStatus, newStatus)) {
            throw TripStopValidationException.invalidSequence(
                String.format("Cannot transition from %s to %s", currentStatus, newStatus));
        }
    }

    private boolean isValidStopStatusTransition(TripStop.StopStatus from, TripStop.StopStatus to) {
        if (from == to) return true;
        
        return switch (from) {
            case PENDING -> to == TripStop.StopStatus.ARRIVED;
            case ARRIVED -> to == TripStop.StopStatus.LOADED || to == TripStop.StopStatus.COMPLETED;
            case LOADED -> to == TripStop.StopStatus.COMPLETED;
            case COMPLETED -> false; // Terminal state
        };
    }

    private void updateTripForNewStop(Trip trip) {
        trip.setTotalStops(trip.getTotalStops() + 1);
        tripRepository.save(trip);
    }

    private void updateTripForRemovedStop(Trip trip) {
        trip.setTotalStops(trip.getTotalStops() - 1);
        
        // Adjust completed stops counter if necessary
        if (trip.getCompletedStops() > trip.getTotalStops()) {
            trip.setCompletedStops(trip.getTotalStops());
        }
        
        tripRepository.save(trip);
    }

    private void updateTripCompletedStops(Trip trip) {
        long actualCompleted = tripStopRepository.countByTrip_IdAndStopStatus(
                trip.getId(), TripStop.StopStatus.COMPLETED);
        trip.setCompletedStops((int) actualCompleted);
        tripRepository.save(trip);
    }

    private void validateStopRemoval(TripStop stop) {
        if (stop.getStopStatus() == TripStop.StopStatus.COMPLETED) {
            throw TripStopValidationException.cannotModifyCompleted();
        }
        
        if (stop.getTrip().getTripStatus() != Trip.TripStatus.ONGOING) {
            throw TripStopValidationException.cannotModifyCompleted();
        }
    }

    private void validateStopReordering(Trip trip) {
        if (trip.getTripStatus() != Trip.TripStatus.ONGOING) {
            throw TripStopValidationException.cannotModifyCompleted();
        }
        
        // Cannot reorder if any stops are completed
        boolean hasCompletedStops = trip.getTripStops().stream()
                .anyMatch(stop -> stop.getStopStatus() == TripStop.StopStatus.COMPLETED);
        
        if (hasCompletedStops) {
            throw TripStopValidationException.invalidSequence(
                "Cannot reorder stops when some are already completed");
        }
    }

    private void validateNewOrdering(List<TripStop> currentStops, List<Long> newOrdering) {
        if (currentStops.size() != newOrdering.size()) {
            throw TripStopValidationException.invalidSequence(
                "New ordering must include all existing stops");
        }
        
        // Verify all current stop IDs are present in new ordering
        for (TripStop stop : currentStops) {
            if (!newOrdering.contains(stop.getId())) {
                throw TripStopValidationException.invalidSequence(
                    "Missing stop ID in new ordering: " + stop.getId());
            }
        }
    }

    private boolean isAdmin(User user) {
        return user.getRole() == User.Role.ADMIN;
    }
}