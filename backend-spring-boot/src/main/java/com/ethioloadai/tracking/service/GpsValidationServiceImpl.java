package com.ethioloadai.tracking.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ethioloadai.tracking.dto.LocationUpdateRequest;
import com.ethioloadai.tracking.dto.GpsValidationResult;
import com.ethioloadai.tracking.dto.GpsValidationResult.ValidationDetails;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Comprehensive GPS validation service with defensible engineering rationales.
 * Implements server-side validation beyond basic Jakarta Bean Validation.
 */
@Service
public class GpsValidationServiceImpl implements GpsValidationService {
    private static final Logger log = LoggerFactory.getLogger(GpsValidationServiceImpl.class);

    // Engineering rationales documented for validation thresholds
    private static final BigDecimal MAX_FUTURE_MINUTES = BigDecimal.valueOf(5); // Clock sync tolerance
    private static final BigDecimal MAX_STALE_MINUTES = BigDecimal.valueOf(30); // Reasonable GPS delay
    private static final BigDecimal SPEED_WARNING_THRESHOLD = BigDecimal.valueOf(150); // Highway speeds
    private static final BigDecimal SPEED_ERROR_THRESHOLD = BigDecimal.valueOf(200); // Bean validation limit
    private static final BigDecimal ACCURACY_WARNING_THRESHOLD = BigDecimal.valueOf(100); // Urban GPS accuracy
    private static final BigDecimal ACCURACY_ERROR_THRESHOLD = BigDecimal.valueOf(500); // Poor GPS conditions
    
    @Override
    public GpsValidationResult validateLocationUpdate(LocationUpdateRequest request) {
        log.debug("Validating GPS location update for vehicle {}", request.getVehicleId());
        
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        ValidationDetails.ValidationDetailsBuilder detailsBuilder = 
            GpsValidationResult.ValidationDetails.builder();
        
        // Validate coordinates
        boolean coordinatesValid = validateCoordinates(request.getLatitude(), request.getLongitude(), errors, detailsBuilder);
        
        // Validate timestamp
        boolean timestampValid = validateTimestamp(request.getTimestamp(), errors, warnings, detailsBuilder);
        
        // Validate numeric values
        boolean numericValid = validateNumericValues(request, errors, detailsBuilder);
        
        // Validate optional fields
        validateOptionalFields(request, warnings, detailsBuilder);
        
        // Calculate quality score
        BigDecimal qualityScore = calculateQualityScore(request, coordinatesValid, timestampValid, numericValid);
        String qualityLevel = determineQualityLevel(qualityScore);
        
        // Build result
        boolean isValid = errors.isEmpty() && coordinatesValid && timestampValid && numericValid;
        
        GpsValidationResult result = GpsValidationResult.builder()
            .isValid(isValid)
            .errors(errors)
            .warnings(warnings)
            .qualityScore(qualityScore)
            .qualityLevel(qualityLevel)
            .details(detailsBuilder.build())
            .build();
            
        log.debug("GPS validation result for vehicle {}: valid={}, quality={}, errors={}, warnings={}", 
                 request.getVehicleId(), isValid, qualityLevel, errors.size(), warnings.size());
                 
        return result;
    }
    
    @Override
    public boolean isValidCoordinates(BigDecimal latitude, BigDecimal longitude) {
        if (latitude == null || longitude == null) {
            return false;
        }
        
        // Geographic bounds validation
        if (latitude.compareTo(BigDecimal.valueOf(-90)) < 0 || 
            latitude.compareTo(BigDecimal.valueOf(90)) > 0) {
            return false;
        }
        
        if (longitude.compareTo(BigDecimal.valueOf(-180)) < 0 || 
            longitude.compareTo(BigDecimal.valueOf(180)) > 0) {
            return false;
        }
        
        // Numeric validity validation
        return isValidNumericValue(latitude, "latitude") && 
               isValidNumericValue(longitude, "longitude");
    }
    
    @Override
    public boolean isValidTimestamp(LocalDateTime timestamp) {
        if (timestamp == null) {
            return false;
        }
        
        LocalDateTime now = LocalDateTime.now();
        
        // Prevent future timestamps (clock sync tolerance: 5 minutes)
        long minutesInFuture = ChronoUnit.MINUTES.between(now, timestamp);
        if (minutesInFuture > MAX_FUTURE_MINUTES.longValue()) {
            return false;
        }
        
        // Reasonable historical bounds (not more than 24 hours old)
        long minutesOld = ChronoUnit.MINUTES.between(timestamp, now);
        if (minutesOld > 1440) { // 24 hours
            return false;
        }
        
        return true;
    }
    
    @Override
    public SpeedValidationResult validateSpeed(BigDecimal speed) {
        if (speed == null) {
            return new SpeedValidationResult(true, false, "Speed not provided", null);
        }
        
        if (!isValidNumericValue(speed, "speed")) {
            return new SpeedValidationResult(false, false, "Invalid speed value", null);
        }
        
        if (speed.compareTo(BigDecimal.ZERO) < 0) {
            return new SpeedValidationResult(false, false, "Speed cannot be negative", BigDecimal.ZERO);
        }
        
        if (speed.compareTo(SPEED_ERROR_THRESHOLD) > 0) {
            return new SpeedValidationResult(false, false, 
                "Speed exceeds maximum limit: " + SPEED_ERROR_THRESHOLD + " km/h", SPEED_ERROR_THRESHOLD);
        }
        
        if (speed.compareTo(SPEED_WARNING_THRESHOLD) > 0) {
            return new SpeedValidationResult(true, true, 
                "High speed detected: " + speed + " km/h", SPEED_WARNING_THRESHOLD);
        }
        
        return new SpeedValidationResult(true, false, "Speed validation passed", null);
    }
    
    @Override
    public AccuracyValidationResult validateAccuracy(BigDecimal accuracy) {
        if (accuracy == null) {
            return new AccuracyValidationResult(true, false, "UNKNOWN", "Accuracy not provided");
        }
        
        if (!isValidNumericValue(accuracy, "accuracy")) {
            return new AccuracyValidationResult(false, false, "INVALID", "Invalid accuracy value");
        }
        
        if (accuracy.compareTo(BigDecimal.ZERO) < 0) {
            return new AccuracyValidationResult(false, false, "INVALID", "Accuracy cannot be negative");
        }
        
        if (accuracy.compareTo(ACCURACY_ERROR_THRESHOLD) > 0) {
            return new AccuracyValidationResult(false, false, "POOR", 
                "GPS accuracy too poor: " + accuracy + "m (limit: " + ACCURACY_ERROR_THRESHOLD + "m)");
        }
        
        // Quality levels based on accuracy
        if (accuracy.compareTo(BigDecimal.valueOf(5)) <= 0) {
            return new AccuracyValidationResult(true, false, "EXCELLENT", "High precision GPS");
        } else if (accuracy.compareTo(BigDecimal.valueOf(20)) <= 0) {
            return new AccuracyValidationResult(true, false, "GOOD", "Good GPS accuracy");
        } else if (accuracy.compareTo(ACCURACY_WARNING_THRESHOLD) <= 0) {
            return new AccuracyValidationResult(true, true, "FAIR", "Moderate GPS accuracy");
        } else {
            return new AccuracyValidationResult(true, true, "POOR", 
                "Poor GPS accuracy: " + accuracy + "m");
        }
    }
    
    @Override
    public boolean isValidNumericValue(BigDecimal value, String fieldName) {
        if (value == null) {
            return true; // Null is valid for optional fields
        }
        
        // Check for NaN and infinite values by converting to double
        try {
            double doubleValue = value.doubleValue();
            if (Double.isNaN(doubleValue) || Double.isInfinite(doubleValue)) {
                log.warn("Invalid numeric value for {}: {}", fieldName, value);
                return false;
            }
            return true;
        } catch (NumberFormatException e) {
            log.warn("Malformed numeric value for {}: {}", fieldName, value);
            return false;
        }
    }
    
    private boolean validateCoordinates(BigDecimal latitude, BigDecimal longitude, 
                                      List<String> errors, 
                                      ValidationDetails.ValidationDetailsBuilder details) {
        boolean valid = isValidCoordinates(latitude, longitude);
        
        if (!valid) {
            if (latitude == null || longitude == null) {
                errors.add("Latitude and longitude are required");
                details.coordinatesMessage("Missing required coordinates");
            } else if (!isValidNumericValue(latitude, "latitude") || !isValidNumericValue(longitude, "longitude")) {
                errors.add("Invalid coordinate values (NaN or infinite)");
                details.coordinatesMessage("Invalid numeric coordinate values");
            } else {
                errors.add("Coordinates outside valid geographic bounds");
                details.coordinatesMessage("Coordinates outside ±90°/±180° bounds");
            }
        } else {
            details.coordinatesMessage("Coordinates valid");
        }
        
        details.coordinatesValid(valid);
        
        // Calculate precision for quality assessment
        if (latitude != null && longitude != null) {
            int latPrecision = Math.max(0, latitude.scale());
            int lngPrecision = Math.max(0, longitude.scale());
            BigDecimal avgPrecision = BigDecimal.valueOf((latPrecision + lngPrecision) / 2.0);
            details.coordinatesPrecision(avgPrecision);
        }
        
        return valid;
    }
    
    private boolean validateTimestamp(LocalDateTime timestamp, List<String> errors, List<String> warnings,
                                    ValidationDetails.ValidationDetailsBuilder details) {
        boolean valid = isValidTimestamp(timestamp);
        
        if (!valid) {
            if (timestamp == null) {
                errors.add("Timestamp is required");
                details.timestampMessage("Missing required timestamp");
            } else {
                LocalDateTime now = LocalDateTime.now();
                long minutesInFuture = ChronoUnit.MINUTES.between(now, timestamp);
                long minutesOld = ChronoUnit.MINUTES.between(timestamp, now);
                
                if (minutesInFuture > MAX_FUTURE_MINUTES.longValue()) {
                    errors.add("Timestamp cannot be more than 5 minutes in the future");
                    details.timestampMessage("Future timestamp detected")
                           .futureTimestamp(true);
                } else if (minutesOld > 1440) {
                    errors.add("Timestamp cannot be more than 24 hours old");  
                    details.timestampMessage("Timestamp too old (>24h)")
                           .minutesOld(minutesOld);
                }
            }
        } else {
            LocalDateTime now = LocalDateTime.now();
            long minutesOld = Math.max(0, ChronoUnit.MINUTES.between(timestamp, now));
            details.minutesOld(minutesOld);
            
            if (minutesOld > MAX_STALE_MINUTES.longValue()) {
                warnings.add("GPS timestamp is stale: " + minutesOld + " minutes old");
                details.timestampMessage("Stale timestamp warning")
                       .staleTimestamp(true);
            } else {
                details.timestampMessage("Timestamp valid")
                       .staleTimestamp(false);
            }
        }
        
        details.timestampValid(valid)
               .futureTimestamp(timestamp != null && timestamp.isAfter(LocalDateTime.now().plusMinutes(1)));
               
        return valid;
    }
    
    private boolean validateNumericValues(LocationUpdateRequest request, List<String> errors,
                                        ValidationDetails.ValidationDetailsBuilder details) {
        List<String> numericErrors = new ArrayList<>();
        
        if (!isValidNumericValue(request.getLatitude(), "latitude")) {
            numericErrors.add("latitude");
        }
        if (!isValidNumericValue(request.getLongitude(), "longitude")) {
            numericErrors.add("longitude");
        }
        if (!isValidNumericValue(request.getSpeed(), "speed")) {
            numericErrors.add("speed");
        }
        if (!isValidNumericValue(request.getAccuracy(), "accuracy")) {
            numericErrors.add("accuracy");
        }
        if (!isValidNumericValue(request.getHeading(), "heading")) {
            numericErrors.add("heading");
        }
        
        boolean valid = numericErrors.isEmpty();
        
        if (!valid) {
            errors.add("Invalid numeric values detected: " + String.join(", ", numericErrors));
            details.numericMessage("Invalid numeric values: " + String.join(", ", numericErrors));
        } else {
            details.numericMessage("All numeric values valid");
        }
        
        details.numericValid(valid);
        return valid;
    }
    
    private void validateOptionalFields(LocationUpdateRequest request, List<String> warnings,
                                      ValidationDetails.ValidationDetailsBuilder details) {
        // Speed validation
        SpeedValidationResult speedResult = validateSpeed(request.getSpeed());
        details.speedValid(speedResult.isValid())
               .speedMessage(speedResult.message());
               
        if (speedResult.threshold() != null) {
            details.speedThreshold(speedResult.threshold());
        }
        
        if (!speedResult.isValid()) {
            warnings.add(speedResult.message());
        } else if (speedResult.isWarning()) {
            warnings.add(speedResult.message());
        }
        
        // Accuracy validation
        AccuracyValidationResult accuracyResult = validateAccuracy(request.getAccuracy());
        details.accuracyValid(accuracyResult.isValid())
               .accuracyMessage(accuracyResult.message())
               .accuracyQuality(accuracyResult.qualityLevel());
               
        if (!accuracyResult.isValid()) {
            warnings.add(accuracyResult.message());
        } else if (accuracyResult.isWarning()) {
            warnings.add(accuracyResult.message());
        }
    }
    
    private BigDecimal calculateQualityScore(LocationUpdateRequest request, 
                                           boolean coordinatesValid, 
                                           boolean timestampValid, 
                                           boolean numericValid) {
        if (!coordinatesValid || !timestampValid || !numericValid) {
            return BigDecimal.ZERO; // Invalid data gets 0 quality
        }
        
        BigDecimal score = BigDecimal.ONE; // Start with perfect score
        
        // Reduce score based on accuracy
        if (request.getAccuracy() != null) {
            AccuracyValidationResult accuracyResult = validateAccuracy(request.getAccuracy());
            switch (accuracyResult.qualityLevel()) {
                case "EXCELLENT" -> score = score.multiply(BigDecimal.valueOf(1.0));
                case "GOOD" -> score = score.multiply(BigDecimal.valueOf(0.9));
                case "FAIR" -> score = score.multiply(BigDecimal.valueOf(0.7));
                case "POOR" -> score = score.multiply(BigDecimal.valueOf(0.5));
            }
        }
        
        // Reduce score for high speeds
        if (request.getSpeed() != null) {
            SpeedValidationResult speedResult = validateSpeed(request.getSpeed());
            if (speedResult.isWarning()) {
                score = score.multiply(BigDecimal.valueOf(0.8));
            }
        }
        
        // Reduce score for stale timestamps
        if (request.getTimestamp() != null) {
            long minutesOld = ChronoUnit.MINUTES.between(request.getTimestamp(), LocalDateTime.now());
            if (minutesOld > MAX_STALE_MINUTES.longValue()) {
                BigDecimal stalePenalty = BigDecimal.valueOf(Math.max(0.5, 1.0 - (minutesOld / 60.0) * 0.1));
                score = score.multiply(stalePenalty);
            }
        }
        
        // Ensure score is between 0 and 1
        return score.max(BigDecimal.ZERO).min(BigDecimal.ONE);
    }
    
    private String determineQualityLevel(BigDecimal qualityScore) {
        if (qualityScore.compareTo(BigDecimal.valueOf(0.9)) >= 0) {
            return "EXCELLENT";
        } else if (qualityScore.compareTo(BigDecimal.valueOf(0.75)) >= 0) {
            return "GOOD";
        } else if (qualityScore.compareTo(BigDecimal.valueOf(0.5)) >= 0) {
            return "FAIR";
        } else {
            return "POOR";
        }
    }
}