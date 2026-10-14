package com.ethioloadai.freight.service;

import com.ethioloadai.exception.AuthorizationException;
import com.ethioloadai.exception.ValidationException;
import com.ethioloadai.freight.dto.*;
import com.ethioloadai.freight.entity.Freight;
import com.ethioloadai.freight.mapper.FreightMapper;
import com.ethioloadai.freight.repository.FreightRepository;
import com.ethioloadai.bid.entity.Bid;
import com.ethioloadai.bid.repository.BidRepository;
import com.ethioloadai.booking.entity.Booking;
import com.ethioloadai.booking.repository.BookingRepository;
import com.ethioloadai.carrier.dispatch.entity.DispatchStatus;
import com.ethioloadai.carrier.dispatch.repository.DispatchAssignmentRepository;
import com.ethioloadai.carrier.offer.entity.CarrierOffer;
import com.ethioloadai.carrier.offer.repository.CarrierOfferRepository;
import com.ethioloadai.trip.entity.Trip;
import com.ethioloadai.trip.repository.TripRepository;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FreightServiceImpl implements FreightService {
    private static final Logger log = LoggerFactory.getLogger(FreightServiceImpl.class);

    private final FreightRepository freightRepository;
    private final UserRepository userRepository;
    private final FreightMapper freightMapper;
    private final BidRepository bidRepository;
    private final BookingRepository bookingRepository;
    private final DispatchAssignmentRepository dispatchRepository;
    private final CarrierOfferRepository carrierOfferRepository;
    private final TripRepository tripRepository;

    @Override
    @Transactional
    public FreightResponse createFreight(Long userId, CreateFreightRequest request) {
        log.info("Creating freight for user ID: {}", userId);

        // Verify user exists and has appropriate role
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthorizationException.ResourceNotFoundException("User"));

        if (user.getRole() != User.Role.SHIPPER && user.getRole() != User.Role.ADMIN) {
            log.warn("Freight creation rejected — userId {} is not a shipper or admin", userId);
            throw new AuthorizationException("Only shippers and admins can create freight");
        }

        // V6: Validate business rules based on service type
        validateCreateFreightRequest(request);

        // Map request to entity
        Freight freight = freightMapper.toEntity(request);
        freight.setShipper(user);
        freight.setStatus(Freight.FreightStatus.POSTED);

        // For intracity freight the DB columns pickup_location, destination, material_type,
        // weight_tons, and urgency_level are NOT NULL (schema predates V6 intracity support).
        // Populate them with sensible defaults so the row saves without a constraint violation.
        if (request.getServiceType() == Freight.ServiceType.INTRACITY) {
            String cityName = request.getCity() != null ? request.getCity() : "Unknown";
            if (freight.getPickupLocation() == null || freight.getPickupLocation().isBlank()) {
                freight.setPickupLocation(cityName + " - " + (request.getPickupArea() != null ? request.getPickupArea() : "pickup"));
            }
            if (freight.getDestination() == null || freight.getDestination().isBlank()) {
                freight.setDestination(cityName + " - " + (request.getDropoffArea() != null ? request.getDropoffArea() : "dropoff"));
            }
            if (freight.getMaterialType() == null || freight.getMaterialType().isBlank()) {
                freight.setMaterialType("general");
            }
            if (freight.getWeightTons() == null) {
                freight.setWeightTons(java.math.BigDecimal.ZERO);
            }
            if (freight.getUrgencyLevel() == null || freight.getUrgencyLevel().isBlank()) {
                freight.setUrgencyLevel("normal");
            }
        }

        // budget is NOT NULL in the DB with CHECK (budget >= 1). For NEGOTIABLE freight
        // the DTO @DecimalMin validation is skipped (budget is optional). Default to 1 ETB
        // so the column constraint is satisfied; drivers bid their own price anyway.
        if (freight.getBudget() == null) {
            freight.setBudget(java.math.BigDecimal.ONE);
        }

        // Calculate and set distance when both coordinate pairs are present.
        // Done after mapping so we only need to set one field rather than rebuilding the DTO.
        if (request.getPickupLatitude() != null && request.getPickupLongitude() != null
                && request.getDestinationLatitude() != null && request.getDestinationLongitude() != null) {
            BigDecimal distance = calculateDistance(
                    request.getPickupLatitude(), request.getPickupLongitude(),
                    request.getDestinationLatitude(), request.getDestinationLongitude());
            freight.setDistanceKm(distance);
            log.debug("Calculated route distance: {} km for freightId (pending save)", distance);
        }

        freight = freightRepository.save(freight);
        log.info("Freight created — freightId: {}, userId: {}", freight.getId(), userId);
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

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthorizationException.ResourceNotFoundException("User"));

        if (user.getRole() != User.Role.SHIPPER && user.getRole() != User.Role.ADMIN) {
            log.warn("getUserFreights rejected — userId {} is not a shipper or admin", userId);
            throw new AuthorizationException("Only shippers and admins can view their freight");
        }

        // findByShipper_Id traverses the @ManyToOne shipper association correctly, ordered latest-first.
        List<Freight> freights = freightRepository.findByShipper_IdOrderByCreatedAtDescIdDesc(userId);
        return freights.stream()
                .map(freightMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<FreightResponse> getUserFreightsPaged(
            Long userId, List<String> requestedStatuses, String materialType,
            String search, Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthorizationException.ResourceNotFoundException("User"));
        if (user.getRole() != User.Role.SHIPPER && user.getRole() != User.Role.ADMIN) {
            throw new AuthorizationException("Only shippers and admins can view their freight");
        }

        List<Freight.FreightStatus> statuses = parseFreightStatuses(requestedStatuses);
        if (statuses.isEmpty()) {
            boolean hasNonBlankStatusFilter = requestedStatuses != null
                    && requestedStatuses.stream().anyMatch(status -> status != null && !status.isBlank());
            if (hasNonBlankStatusFilter) {
                return Page.empty(pageable);
            }
            statuses = List.of(Freight.FreightStatus.values());
        }
        String safeMaterialType = materialType == null || materialType.isBlank()
                ? null : materialType;
        String searchPattern = search == null || search.isEmpty()
                ? null : "%" + escapeLikeLiteral(search.toLowerCase(Locale.ROOT)) + "%";

        return freightRepository.findShipperFreightPage(
                        userId, statuses, safeMaterialType, searchPattern, pageable)
                .map(freightMapper::toResponse);
    }

    private List<Freight.FreightStatus> parseFreightStatuses(List<String> requestedStatuses) {
        if (requestedStatuses == null || requestedStatuses.isEmpty()) {
            return List.of(Freight.FreightStatus.values());
        }
        List<Freight.FreightStatus> statuses = new ArrayList<>();
        for (String requestedStatus : requestedStatuses) {
            if (requestedStatus == null || requestedStatus.isBlank()) {
                continue;
            }
            try {
                Freight.FreightStatus status = Freight.FreightStatus.valueOf(
                        requestedStatus.trim().toUpperCase(Locale.ROOT));
                if (!statuses.contains(status)) {
                    statuses.add(status);
                }
            } catch (IllegalArgumentException exception) {
                log.warn("Ignoring unknown shipper freight status filter: '{}'", requestedStatus);
            }
        }
        return statuses;
    }

    private String escapeLikeLiteral(String value) {
        return value.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    /**
     * Paginated public freight search.
     *
     * <p>Uses {@link Pageable} to push limit/offset into the database query rather than
     * loading the entire freight table into memory. Callers must always provide a
     * {@link Pageable} — the controller defaults to page 0 / size 20 when not specified.
     */
    @Override
    @Transactional(readOnly = true)
    public Page<FreightResponse> searchFreightsPaged(FreightFilterRequest filter, Pageable pageable) {
        log.info("Searching freight — filter: {}, page: {}, size: {}",
                filter, pageable.getPageNumber(), pageable.getPageSize());

        Specification<Freight> spec = buildSpecification(filter);
        Page<Freight> page = freightRepository.findAll(spec, pageable);

        log.debug("Freight search returned {}/{} results (page {}/{})",
                page.getNumberOfElements(), page.getTotalElements(),
                page.getNumber(), page.getTotalPages());

        return page.map(freightMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<FreightResponse> searchFreightsPaged(FreightFilterRequest filter, Pageable pageable, User caller) {
        Specification<Freight> spec = buildSpecification(filter);
        if (caller != null && caller.getRole() == User.Role.SHIPPER) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("shipper").get("id"), caller.getId()));
        }
        return freightRepository.findAll(spec, pageable).map(freightMapper::toResponse);
    }

    @Override
    @Transactional
    public FreightResponse updateFreight(Long id, Long userId, UpdateFreightRequest request) {
        log.info("Updating freight ID: {} for userId: {}", id, userId);

        Freight freight = request.getStatus() == Freight.FreightStatus.CANCELLED
                ? freightRepository.findByIdForUpdate(id)
                        .orElseThrow(() -> new AuthorizationException.ResourceNotFoundException("Freight"))
                : freightRepository.findById(id)
                .orElseThrow(() -> new AuthorizationException.ResourceNotFoundException("Freight"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthorizationException.ResourceNotFoundException("User"));

        if (!freight.getShipper().getId().equals(userId) && user.getRole() != User.Role.ADMIN) {
            log.warn("updateFreight rejected — userId {} does not own freight {}", userId, id);
            throw new AuthorizationException.OwnershipException();
        }

        if (request.getStatus() == Freight.FreightStatus.MATCHED) {
            throw new ValidationException(
                    "Freight cannot be marked MATCHED through a generic update",
                    "status",
                    "Freight can only be matched through bid or dispatch acceptance");
        }

        // V6: Validate update request
        validateUpdateFreightRequest(request, freight);

        if (request.getStatus() != null && !isValidStatusTransition(freight.getStatus(), request.getStatus())) {
            throw new ValidationException(
                    "Invalid status transition from " + freight.getStatus() + " to " + request.getStatus(),
                    "status",
                    "Cannot transition from " + freight.getStatus() + " to " + request.getStatus());
        }

        if (request.getStatus() == Freight.FreightStatus.CANCELLED) {
            validateCancellationAllowed(id, freight);
        }

        freightMapper.updateEntityFromDto(request, freight);
        freight = freightRepository.save(freight);

        log.info("Freight updated — freightId: {}", id);
        return freightMapper.toResponse(freight);
    }

    @Override
    @Transactional
    public void cancelFreight(Long id, Long userId) {
        log.info("Cancelling freight ID: {} for userId: {}", id, userId);

        Freight freight = freightRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new AuthorizationException.ResourceNotFoundException("Freight"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthorizationException.ResourceNotFoundException("User"));

        if (!freight.getShipper().getId().equals(userId) && user.getRole() != User.Role.ADMIN) {
            log.warn("cancelFreight rejected — userId {} does not own freight {}", userId, id);
            throw new AuthorizationException.OwnershipException();
        }

        validateCancellationAllowed(id, freight);

        freight.setStatus(Freight.FreightStatus.CANCELLED);
        freightRepository.save(freight);
        log.info("Freight cancelled — freightId: {}", id);
    }

    private void validateCancellationAllowed(Long id, Freight freight) {
        if (freight.getStatus() != Freight.FreightStatus.POSTED) {
            throw new ValidationException(
                    "Can only cancel freight in POSTED status",
                    "status",
                    "Freight cannot be cancelled in current status: " + freight.getStatus());
        }

        boolean hasActiveBid = bidRepository.existsByCargoRequest_IdAndStatusIn(
                id, List.of(Bid.BidStatus.PENDING, Bid.BidStatus.COUNTERED, Bid.BidStatus.ACCEPTED));
        boolean hasActiveOffer = carrierOfferRepository.existsByFreight_IdAndStatusIn(
                id, List.of(CarrierOffer.OfferStatus.PENDING, CarrierOffer.OfferStatus.NEGOTIATING,
                        CarrierOffer.OfferStatus.ACCEPTED));
        boolean hasActiveDispatch = dispatchRepository.existsByFreightIdAndStatusIn(
                id, List.of(DispatchStatus.ASSIGNED, DispatchStatus.DRIVER_ACCEPTED));
        boolean hasActiveBooking = bookingRepository.existsByCargoRequest_IdAndBookingStatusIn(
                id, List.of(Booking.BookingStatus.PENDING, Booking.BookingStatus.ACCEPTED,
                        Booking.BookingStatus.CONFIRMED));
        boolean hasActiveTrip = tripRepository.existsByBooking_CargoRequest_IdAndTripStatusIn(
                id, List.of(Trip.TripStatus.ONGOING, Trip.TripStatus.ARRIVED,
                        Trip.TripStatus.DELIVERY_PENDING_VERIFICATION));
        boolean hasMatchingState = freight.getMatchedDriver() != null
                || freight.getMatchedVehicle() != null
                || freight.getMatchedPrice() != null
                || freight.getCarrierAwardOfferId() != null;
        if (hasActiveBid || hasActiveOffer || hasActiveDispatch || hasActiveBooking
                || hasActiveTrip || hasMatchingState) {
            throw new ValidationException(
                    "Freight cannot be cancelled while an active bid, offer, award, dispatch, booking, trip, or matching assignment exists",
                    "status",
                    "Cancel or resolve active freight workflows before cancelling the freight");
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private Specification<Freight> buildSpecification(FreightFilterRequest filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.getStatus() != null && !filter.getStatus().isBlank()) {
                try {
                    Freight.FreightStatus statusEnum =
                            Freight.FreightStatus.valueOf(filter.getStatus().toUpperCase());
                    predicates.add(cb.equal(root.get("status"), statusEnum));
                } catch (IllegalArgumentException e) {
                    // Unknown status value — treat as no filter rather than throwing
                    log.warn("Ignoring unknown freight status filter value: '{}'", filter.getStatus());
                }
            }

            if (filter.getMaterialType() != null && !filter.getMaterialType().isBlank()) {
                predicates.add(cb.equal(root.get("materialType"), filter.getMaterialType()));
            }

            if (filter.getPickupLocation() != null && !filter.getPickupLocation().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("pickupLocation")),
                        "%" + filter.getPickupLocation().toLowerCase() + "%"));
            }

            if (filter.getDestination() != null && !filter.getDestination().isBlank()) {
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

            // ── V6 filter enhancements ─────────────────────────────────────────

            // Service type filtering (intercity vs intracity)
            if (filter.getServiceType() != null && !filter.getServiceType().isBlank()) {
                try {
                    Freight.ServiceType serviceTypeEnum =
                            Freight.ServiceType.valueOf(filter.getServiceType().toUpperCase());
                    predicates.add(cb.equal(root.get("serviceType"), serviceTypeEnum));
                } catch (IllegalArgumentException e) {
                    log.warn("Ignoring unknown service type filter value: '{}'", filter.getServiceType());
                }
            }

            // Price type filtering (negotiable vs fixed)
            if (filter.getPriceType() != null && !filter.getPriceType().isBlank()) {
                try {
                    Freight.PriceType priceTypeEnum =
                            Freight.PriceType.valueOf(filter.getPriceType().toUpperCase());
                    predicates.add(cb.equal(root.get("priceType"), priceTypeEnum));
                } catch (IllegalArgumentException e) {
                    log.warn("Ignoring unknown price type filter value: '{}'", filter.getPriceType());
                }
            }

            // City-based filtering (for intracity freight browsing)
            if (filter.getCity() != null && !filter.getCity().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("city")),
                        "%" + filter.getCity().toLowerCase() + "%"));
            }

            // Pickup area filtering (intracity)
            if (filter.getPickupArea() != null && !filter.getPickupArea().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("pickupArea")),
                        "%" + filter.getPickupArea().toLowerCase() + "%"));
            }

            // Dropoff area filtering (intracity)
            if (filter.getDropoffArea() != null && !filter.getDropoffArea().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("dropoffArea")),
                        "%" + filter.getDropoffArea().toLowerCase() + "%"));
            }

            // Preferred date filtering
            if (filter.getPreferredDateAfter() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("preferredDate"), filter.getPreferredDateAfter()));
            }

            // Vehicle type filtering
            if (filter.getVehicleTypeNeeded() != null && !filter.getVehicleTypeNeeded().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("vehicleTypeNeeded")),
                        "%" + filter.getVehicleTypeNeeded().toLowerCase() + "%"));
            }

            // Bids still open filtering (for drivers to find active opportunities)
            if (filter.getBidsOpen() != null && filter.getBidsOpen()) {
                predicates.add(cb.and(
                    cb.equal(root.get("priceType"), Freight.PriceType.NEGOTIABLE),
                    cb.equal(root.get("status"), Freight.FreightStatus.POSTED),
                    cb.or(
                        cb.isNull(root.get("bidDeadline")),
                        cb.greaterThan(root.get("bidDeadline"), cb.currentTimestamp())
                    )
                ));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private boolean isValidStatusTransition(Freight.FreightStatus current, Freight.FreightStatus target) {
        return switch (current) {
            case POSTED    -> target == Freight.FreightStatus.MATCHED   || target == Freight.FreightStatus.CANCELLED;
            case MATCHED   -> target == Freight.FreightStatus.IN_TRANSIT || target == Freight.FreightStatus.CANCELLED;
            case IN_TRANSIT -> target == Freight.FreightStatus.DELIVERED;
            case DELIVERED  -> target == Freight.FreightStatus.COMPLETED;
            case COMPLETED, CANCELLED -> false; // terminal states
        };
    }

    /** Haversine great-circle distance in km, rounded to 2 decimal places. */
    private BigDecimal calculateDistance(BigDecimal lat1, BigDecimal lon1,
                                         BigDecimal lat2, BigDecimal lon2) {
        final int R = 6371;
        double lat1Rad = Math.toRadians(lat1.doubleValue());
        double lat2Rad = Math.toRadians(lat2.doubleValue());
        double deltaLat = Math.toRadians(lat2.subtract(lat1).doubleValue());
        double deltaLon = Math.toRadians(lon2.subtract(lon1).doubleValue());

        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(lat1Rad) * Math.cos(lat2Rad)
                * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return BigDecimal.valueOf(R * c).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * V6: Validates create freight request based on service type business rules.
     * Implements intracity vs intercity validation logic from Laravel reference.
     */
    private void validateCreateFreightRequest(CreateFreightRequest request) {
        if (request.getServiceType() == Freight.ServiceType.INTRACITY) {
            // Intracity validation rules (based on Laravel CargoCreateRequest)
            if (request.getCity() == null || request.getCity().isBlank()) {
                throw new ValidationException("City is required for intracity freight", "city", "City cannot be blank for intracity service");
            }
            if (request.getPickupArea() == null || request.getPickupArea().isBlank()) {
                throw new ValidationException("Pickup area is required for intracity freight", "pickupArea", "Pickup area cannot be blank for intracity service");
            }
            if (request.getDropoffArea() == null || request.getDropoffArea().isBlank()) {
                throw new ValidationException("Dropoff area is required for intracity freight", "dropoffArea", "Dropoff area cannot be blank for intracity service");
            }
            if (request.getPreferredDate() == null) {
                throw new ValidationException("Preferred date is required for intracity freight", "preferredDate", "Preferred date cannot be null for intracity service");
            }
            if (request.getPreferredDate().isBefore(LocalDate.now())) {
                throw new ValidationException("Preferred date must be today or in the future", "preferredDate", "Preferred date cannot be in the past");
            }
            if (request.getItemsDescription() == null || request.getItemsDescription().isBlank()) {
                throw new ValidationException("Items description is required for intracity freight", "itemsDescription", "Items description cannot be blank for intracity service");
            }
        } else {
            // Intercity validation rules (traditional freight)
            if (request.getPickupLocation() == null || request.getPickupLocation().isBlank()) {
                throw new ValidationException("Pickup location is required for intercity freight", "pickupLocation", "Pickup location cannot be blank for intercity service");
            }
            if (request.getDestination() == null || request.getDestination().isBlank()) {
                throw new ValidationException("Destination is required for intercity freight", "destination", "Destination cannot be blank for intercity service");
            }
            if (request.getMaterialType() == null || request.getMaterialType().isBlank()) {
                throw new ValidationException("Material type is required for intercity freight", "materialType", "Material type cannot be blank for intercity service");
            }
            if (request.getWeightTons() == null) {
                throw new ValidationException("Weight is required for intercity freight", "weightTons", "Weight cannot be null for intercity service");
            }
            if (request.getUrgencyLevel() == null || request.getUrgencyLevel().isBlank()) {
                throw new ValidationException("Urgency level is required for intercity freight", "urgencyLevel", "Urgency level cannot be blank for intercity service");
            }
        }

        // Price type validation (applies to both service types)
        if (request.getPriceType() == Freight.PriceType.FIXED && (request.getBudget() == null || request.getBudget().compareTo(BigDecimal.ZERO) <= 0)) {
            throw new ValidationException("Budget is required for fixed-price freight", "budget", "Fixed-price freight must have a positive budget");
        }
        
        // Bid deadline validation
        if (request.getBidDeadline() != null && request.getBidDeadline().isBefore(LocalDateTime.now())) {
            throw new ValidationException("Bid deadline must be in the future", "bidDeadline", "Bid deadline cannot be in the past");
        }
        
        // Negotiable pricing should typically have a bid deadline
        if (request.getPriceType() == Freight.PriceType.NEGOTIABLE && request.getBidDeadline() == null) {
            log.warn("Negotiable pricing without bid deadline - bids will remain open indefinitely");
        }
    }

    /**
     * V6: Validates update freight request based on current state and business rules.
     */
    private void validateUpdateFreightRequest(UpdateFreightRequest request, Freight freight) {
        // Price type changes only allowed in POSTED status
        if (request.getPriceType() != null && freight.getStatus() != Freight.FreightStatus.POSTED) {
            throw new ValidationException("Price type can only be changed while freight is in POSTED status", "priceType", "Cannot modify price type after freight matching starts");
        }
        
        // Fixed price requires budget
        if (request.getPriceType() == Freight.PriceType.FIXED && (request.getBudget() == null || request.getBudget().compareTo(BigDecimal.ZERO) <= 0)) {
            // Check existing budget if new budget not provided
            if (freight.getBudget() == null || freight.getBudget().compareTo(BigDecimal.ZERO) <= 0) {
                throw new ValidationException("Budget is required for fixed-price freight", "budget", "Fixed-price freight must have a positive budget");
            }
        }
        
        // Bid deadline validation
        if (request.getBidDeadline() != null && request.getBidDeadline().isBefore(LocalDateTime.now())) {
            throw new ValidationException("Bid deadline must be in the future", "bidDeadline", "Bid deadline cannot be in the past");
        }

        // Preferred date validation
        if (request.getPreferredDate() != null && request.getPreferredDate().isBefore(LocalDate.now())) {
            throw new ValidationException("Preferred date must be today or in the future", "preferredDate", "Preferred date cannot be in the past");
        }

        // Intracity-specific validations for updates
        if (freight.getServiceType() == Freight.ServiceType.INTRACITY) {
            if (request.getCity() != null && request.getCity().isBlank()) {
                throw new ValidationException("City cannot be empty for intracity freight", "city", "City is required for intracity service");
            }
            if (request.getPickupArea() != null && request.getPickupArea().isBlank()) {
                throw new ValidationException("Pickup area cannot be empty for intracity freight", "pickupArea", "Pickup area is required for intracity service");
            }
            if (request.getDropoffArea() != null && request.getDropoffArea().isBlank()) {
                throw new ValidationException("Dropoff area cannot be empty for intracity freight", "dropoffArea", "Dropoff area is required for intracity service");
            }
        }
    }
}