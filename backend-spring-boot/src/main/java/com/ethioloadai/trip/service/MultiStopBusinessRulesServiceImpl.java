package com.ethioloadai.trip.service;

import com.ethioloadai.freight.entity.Freight;
import com.ethioloadai.freight.repository.FreightRepository;
import com.ethioloadai.trip.config.TripManagementProperties;
import com.ethioloadai.trip.entity.Trip;
import com.ethioloadai.trip.entity.TripStop;
import com.ethioloadai.trip.repository.TripRepository;
import com.ethioloadai.trip.repository.TripStopRepository;
import com.ethioloadai.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MultiStopBusinessRulesServiceImpl implements MultiStopBusinessRulesService {
    private static final Logger log = LoggerFactory.getLogger(MultiStopBusinessRulesServiceImpl.class);

    private final TripRepository tripRepository;
    private final TripStopRepository tripStopRepository;
    private final FreightRepository freightRepository;
    private final TripManagementProperties properties;

    // Business constants from Laravel reference
    private static final BigDecimal COMMISSION_RATE = new BigDecimal("0.10"); // 10%
    private static final BigDecimal BASE_RATE_MIN = new BigDecimal("18"); // ETB per km per ton
    private static final BigDecimal BASE_RATE_MAX = new BigDecimal("28");
    private static final BigDecimal ROAD_DISTANCE_MULTIPLIER = new BigDecimal("1.6"); // Haversine to road distance
    private static final String CURRENCY = "ETB";
    private static final int PRICE_ROUNDING_INCREMENT = 500; // Round to nearest 500 ETB

    // Material multipliers from Laravel
    private static final Map<String, BigDecimal> MATERIAL_MULTIPLIERS = Map.of(
        "fragile", new BigDecimal("1.25"),      // glass, electronic, ceramic, machinery, medical
        "perishable", new BigDecimal("1.15"),   // vegetable, fruit, dairy, fish, meat, teff, grain, coffee
        "bulk", new BigDecimal("0.90"),         // cement, sand, gravel, scrap, construction, stone
        "standard", new BigDecimal("1.00")
    );

    // Urgency multipliers
    private static final Map<String, BigDecimal> URGENCY_MULTIPLIERS = Map.of(
        "express", new BigDecimal("1.40"),
        "high", new BigDecimal("1.20"),
        "standard", new BigDecimal("1.00"),
        "low", new BigDecimal("0.90")
    );

    @Override
    public BigDecimal calculateTripTotalAmount(Trip trip) {
        if (trip.getTripStops() == null || trip.getTripStops().isEmpty()) {
            return BigDecimal.ZERO;
        }

        return trip.getTripStops().stream()
                .map(TripStop::getAgreedPrice)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    public CommissionCalculation calculateMultiStopCommission(Trip trip) {
        BigDecimal totalAmount = calculateTripTotalAmount(trip);
        BigDecimal commissionAmount = totalAmount.multiply(COMMISSION_RATE)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal driverNetAmount = totalAmount.subtract(commissionAmount);

        log.debug("Commission calculation for trip {}: total={}, commission={}, net={}", 
                 trip.getId(), totalAmount, commissionAmount, driverNetAmount);

        return new CommissionCalculation(
            totalAmount,
            COMMISSION_RATE,
            commissionAmount,
            driverNetAmount,
            CURRENCY
        );
    }

    @Override
    public SequentialCompletionValidation validateSequentialCompletion(TripStop stop, TripStop.StopStatus targetStatus) {
        List<String> violations = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        TripStop previousStop = null;
        TripStop.StopStatus requiredPreviousStatus = null;

        // Only validate for arrival and completion transitions
        if (targetStatus != TripStop.StopStatus.ARRIVED && targetStatus != TripStop.StopStatus.COMPLETED) {
            return new SequentialCompletionValidation(true, violations, warnings, null, null);
        }

        // Get previous stop if not first
        if (stop.getStopOrder() > 1) {
            Optional<TripStop> prevOpt = tripStopRepository.findByTrip_IdAndStopOrder(
                stop.getTrip().getId(), stop.getStopOrder() - 1);
            
            if (prevOpt.isPresent()) {
                previousStop = prevOpt.get();
                requiredPreviousStatus = TripStop.StopStatus.COMPLETED;

                if (previousStop.getStopStatus() != TripStop.StopStatus.COMPLETED) {
                    violations.add(String.format(
                        "Previous stop %d (%s) must be completed before %s stop %d", 
                        previousStop.getStopOrder(), 
                        previousStop.getLocationName(),
                        targetStatus.name().toLowerCase(),
                        stop.getStopOrder()
                    ));
                }
            }
        }

        boolean isValid = violations.isEmpty();
        
        log.debug("Sequential validation for stop {} → {}: valid={}, violations={}", 
                 stop.getId(), targetStatus, isValid, violations.size());

        return new SequentialCompletionValidation(
            isValid, violations, warnings, previousStop, requiredPreviousStatus
        );
    }

    @Override
    public PricingEstimate calculateStopPricing(String fromLocation, String toLocation, 
                                               BigDecimal weight, String material, String urgency) {
        
        // Calculate distance (simplified - in production would use OSRM)
        BigDecimal distanceKm = calculateDistance(fromLocation, toLocation);
        
        // Get multipliers
        BigDecimal materialMultiplier = getMaterialMultiplier(material);
        BigDecimal urgencyMultiplier = getUrgencyMultiplier(urgency);
        
        // Calculate base prices
        BigDecimal baseMin = distanceKm
                .multiply(BASE_RATE_MIN)
                .multiply(weight)
                .multiply(urgencyMultiplier)
                .multiply(materialMultiplier);
        
        BigDecimal baseMax = distanceKm
                .multiply(BASE_RATE_MAX)
                .multiply(weight)
                .multiply(urgencyMultiplier)
                .multiply(materialMultiplier);

        // Round to nearest 500 ETB
        BigDecimal priceMin = roundToIncrement(baseMin, PRICE_ROUNDING_INCREMENT);
        BigDecimal priceMax = roundToIncrement(baseMax, PRICE_ROUNDING_INCREMENT);
        BigDecimal estimatedPrice = priceMin.add(priceMax).divide(new BigDecimal("2"), RoundingMode.HALF_UP);

        Map<String, BigDecimal> multipliers = Map.of(
            "material", materialMultiplier,
            "urgency", urgencyMultiplier,
            "distance", distanceKm
        );

        log.debug("Pricing estimate for {} → {}: distance={}km, weight={}t, price={}", 
                 fromLocation, toLocation, distanceKm, weight, estimatedPrice);

        return new PricingEstimate(
            priceMin,
            priceMax, 
            estimatedPrice,
            distanceKm,
            CURRENCY,
            new BigDecimal("0.80"), // 80% confidence for local estimates
            "local_estimate",
            multipliers
        );
    }

    @Override
    public StopAdditionValidation validateStopAddition(Trip trip, Integer stopOrder, 
                                                      Long cargoRequestId, User user) {
        List<String> violations = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        // Trip must be ongoing
        if (trip.getTripStatus() != Trip.TripStatus.ONGOING) {
            violations.add("Stops can only be added to ongoing trips");
        }

        // Validate stop order
        if (stopOrder != null && stopOrder < 1) {
            violations.add("Stop order must be positive integer");
        }

        // Check if stop order already exists
        if (stopOrder != null) {
            Optional<TripStop> existingStop = tripStopRepository.findByTrip_IdAndStopOrder(
                trip.getId(), stopOrder);
            if (existingStop.isPresent()) {
                violations.add(String.format("Stop order %d already exists", stopOrder));
            }
        }

        // Validate cargo request if provided
        if (cargoRequestId != null) {
            Optional<Freight> cargoOpt = freightRepository.findById(cargoRequestId);
            if (cargoOpt.isEmpty()) {
                violations.add(String.format("Cargo request %d not found", cargoRequestId));
            } else {
                Freight cargo = cargoOpt.get();
                if (cargo.getStatus() != Freight.FreightStatus.MATCHED) {
                    warnings.add("Cargo request is not in MATCHED status");
                }
            }
        }

        Integer suggestedOrder = stopOrder != null ? stopOrder : getNextStopOrder(trip);
        BigDecimal estimatedPrice = BigDecimal.ZERO; // Would calculate based on route

        boolean canAdd = violations.isEmpty();

        return new StopAdditionValidation(canAdd, violations, warnings, suggestedOrder, estimatedPrice);
    }

    @Override
    @Transactional
    public void updateTripTypeForStopCount(Trip trip) {
        Trip.TripType newType = trip.getTotalStops() > 1 ? Trip.TripType.MULTI_STOP : Trip.TripType.SINGLE;
        
        if (trip.getTripType() != newType) {
            log.info("Updating trip {} type from {} to {} (stops: {})", 
                    trip.getId(), trip.getTripType(), newType, trip.getTotalStops());
            
            trip.setTripType(newType);
            tripRepository.save(trip);
        }
    }

    @Override
    public RouteOptimization optimizeStopRoute(Trip trip) {
        // Simplified optimization - in production would use advanced routing algorithms
        List<TripStop> stops = new ArrayList<>(trip.getTripStops());
        stops.sort(Comparator.comparing(TripStop::getStopOrder));

        BigDecimal totalDistance = BigDecimal.ZERO;
        for (int i = 0; i < stops.size() - 1; i++) {
            totalDistance = totalDistance.add(calculateDistance(
                stops.get(i).getLocationName(),
                stops.get(i + 1).getLocationName()
            ));
        }

        BigDecimal estimatedDuration = totalDistance.divide(new BigDecimal("60"), RoundingMode.HALF_UP); // 60 km/h average
        BigDecimal fuelSavings = BigDecimal.ZERO; // Would calculate potential savings

        return new RouteOptimization(
            stops,
            totalDistance,
            estimatedDuration,
            fuelSavings,
            "sequential_order"
        );
    }

    @Override
    public Integer getNextStopOrder(Trip trip) {
        return tripStopRepository.nextStopOrder(trip.getId());
    }

    @Override
    @Transactional
    public void resequenceStopsAfterDeletion(Trip trip, Integer deletedOrder) {
        List<TripStop> stopsToReorder = tripStopRepository.findByTrip_IdAndStopOrderGreaterThan(
            trip.getId(), deletedOrder);
        
        for (TripStop stop : stopsToReorder) {
            stop.setStopOrder(stop.getStopOrder() - 1);
            tripStopRepository.save(stop);
        }

        log.info("Resequenced {} stops after deleting order {} from trip {}", 
                stopsToReorder.size(), deletedOrder, trip.getId());
    }

    @Override
    public BigDecimal calculateDistance(String fromLocation, String toLocation) {
        // Simplified distance calculation - in production would use:
        // 1. OSRM routing service for real road distances
        // 2. City-to-city distance lookup tables
        // 3. GPS coordinate haversine calculations
        
        if (fromLocation == null || toLocation == null) {
            return BigDecimal.ZERO;
        }

        // Mock distance based on location names (for testing)
        // In production, integrate with OSRM or Google Maps API
        String normalizedFrom = normalizeCity(fromLocation);
        String normalizedTo = normalizeCity(toLocation);
        
        if (normalizedFrom.equals(normalizedTo)) {
            return BigDecimal.ZERO;
        }

        // Return mock distance - would be replaced with real calculation
        return new BigDecimal("150"); // 150km default
    }

    @Override
    public CargoValidationResult validateCargoRequests(List<Long> cargoRequestIds, User user) {
        List<Long> validIds = new ArrayList<>();
        List<Long> invalidIds = new ArrayList<>();
        Map<Long, String> errors = new HashMap<>();

        for (Long cargoId : cargoRequestIds) {
            Optional<Freight> cargoOpt = freightRepository.findById(cargoId);
            if (cargoOpt.isEmpty()) {
                invalidIds.add(cargoId);
                errors.put(cargoId, "Cargo request not found");
            } else {
                Freight cargo = cargoOpt.get();
                if (cargo.getStatus() != Freight.FreightStatus.MATCHED) {
                    invalidIds.add(cargoId);
                    errors.put(cargoId, "Cargo not in MATCHED status");
                } else {
                    validIds.add(cargoId);
                }
            }
        }

        boolean allValid = invalidIds.isEmpty();
        return new CargoValidationResult(validIds, invalidIds, errors, allValid);
    }

    // ── Private Helper Methods ───────────────────────────────────────────────

    private BigDecimal getMaterialMultiplier(String material) {
        if (material == null) {
            return MATERIAL_MULTIPLIERS.get("standard");
        }
        
        String normalized = material.toLowerCase();
        
        // Fragile materials
        if (containsAny(normalized, "glass", "electronic", "ceramic", "machinery", "medical")) {
            return MATERIAL_MULTIPLIERS.get("fragile");
        }
        
        // Perishable materials
        if (containsAny(normalized, "vegetable", "fruit", "dairy", "fish", "meat", "teff", "grain", "coffee")) {
            return MATERIAL_MULTIPLIERS.get("perishable");
        }
        
        // Bulk materials
        if (containsAny(normalized, "cement", "sand", "gravel", "scrap", "construction", "stone")) {
            return MATERIAL_MULTIPLIERS.get("bulk");
        }
        
        return MATERIAL_MULTIPLIERS.get("standard");
    }

    private BigDecimal getUrgencyMultiplier(String urgency) {
        if (urgency == null) {
            return URGENCY_MULTIPLIERS.get("standard");
        }
        return URGENCY_MULTIPLIERS.getOrDefault(urgency.toLowerCase(), URGENCY_MULTIPLIERS.get("standard"));
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private BigDecimal roundToIncrement(BigDecimal value, int increment) {
        long rounded = Math.round(value.doubleValue() / increment) * increment;
        return new BigDecimal(rounded);
    }

    private String normalizeCity(String city) {
        if (city == null) return "";
        return city.trim().toLowerCase()
                .replace("addis ababa", "addis_ababa")
                .replace(" ", "_");
    }
}