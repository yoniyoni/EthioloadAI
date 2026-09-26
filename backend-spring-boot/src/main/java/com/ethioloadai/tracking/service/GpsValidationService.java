package com.ethioloadai.tracking.service;

import com.ethioloadai.tracking.dto.LocationUpdateRequest;
import com.ethioloadai.tracking.dto.GpsValidationResult;

/**
 * Service for comprehensive GPS coordinate and data validation.
 * Implements server-side validation beyond basic Jakarta Bean Validation.
 */
public interface GpsValidationService {

    /**
     * Performs comprehensive GPS data validation including:
     * - Geographic bounds validation
     * - Timestamp validation (prevents future timestamps)
     * - Coordinate precision validation
     * - Numeric validity (NaN, infinite values)
     * - Speed reasonableness checks
     * - Accuracy assessment
     *
     * @param request GPS location update request
     * @return validation result with issues and quality score
     */
    GpsValidationResult validateLocationUpdate(LocationUpdateRequest request);

    /**
     * Validates GPS coordinates are within valid geographic bounds
     * and have appropriate precision for vehicle tracking.
     *
     * @param latitude GPS latitude in decimal degrees
     * @param longitude GPS longitude in decimal degrees  
     * @return true if coordinates are valid, false otherwise
     */
    boolean isValidCoordinates(java.math.BigDecimal latitude, java.math.BigDecimal longitude);

    /**
     * Validates timestamp is not in the future and within reasonable bounds.
     * Engineering rationale: GPS timestamps more than 5 minutes in future
     * are rejected as likely clock synchronization issues.
     *
     * @param timestamp GPS reading timestamp
     * @return true if timestamp is valid, false otherwise
     */
    boolean isValidTimestamp(java.time.LocalDateTime timestamp);

    /**
     * Validates speed is reasonable for vehicle tracking.
     * Flags speeds over 150 km/h as warnings, rejects over 200 km/h.
     *
     * @param speed Vehicle speed in km/h
     * @return speed validation result
     */
    SpeedValidationResult validateSpeed(java.math.BigDecimal speed);

    /**
     * Validates GPS accuracy is within acceptable bounds.
     * Accuracy over 100m triggers warnings, over 500m triggers errors.
     *
     * @param accuracy GPS accuracy radius in meters
     * @return accuracy validation result
     */
    AccuracyValidationResult validateAccuracy(java.math.BigDecimal accuracy);

    /**
     * Validates numeric values are not NaN, infinite, or malformed.
     *
     * @param value Numeric value to validate
     * @param fieldName Field name for error reporting
     * @return true if numeric value is valid
     */
    boolean isValidNumericValue(java.math.BigDecimal value, String fieldName);

    /**
     * Speed validation result with threshold analysis.
     */
    record SpeedValidationResult(
        boolean isValid,
        boolean isWarning,
        String message,
        java.math.BigDecimal threshold
    ) {}

    /**
     * Accuracy validation result with quality assessment.
     */
    record AccuracyValidationResult(
        boolean isValid,
        boolean isWarning,
        String qualityLevel, // EXCELLENT, GOOD, FAIR, POOR
        String message
    ) {}
}