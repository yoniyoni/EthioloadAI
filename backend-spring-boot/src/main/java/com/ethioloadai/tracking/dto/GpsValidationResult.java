package com.ethioloadai.tracking.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Comprehensive GPS validation result with quality assessment.
 * Used by GpsValidationService to report validation outcomes.
 */
@Data
@Builder
public class GpsValidationResult {

    /**
     * Overall validation result.
     * false = GPS data should be rejected
     * true = GPS data can be accepted (may have warnings)
     */
    private boolean isValid;

    /**
     * List of blocking validation issues that cause rejection.
     * Examples: invalid coordinates, future timestamps, malformed data
     */
    @Builder.Default
    private List<String> errors = List.of();

    /**
     * List of non-blocking warnings that don't cause rejection.
     * Examples: poor accuracy, high speed, old timestamp
     */
    @Builder.Default  
    private List<String> warnings = List.of();

    /**
     * Overall GPS data quality score (0.0 to 1.0).
     * Based on accuracy, timestamp freshness, speed reasonableness.
     * 1.0 = Excellent quality, 0.0 = Poor quality
     */
    private BigDecimal qualityScore;

    /**
     * Human-readable quality assessment.
     * Values: EXCELLENT, GOOD, FAIR, POOR
     */
    private String qualityLevel;

    /**
     * Detailed validation breakdown for debugging.
     */
    @Builder.Default
    private ValidationDetails details = ValidationDetails.builder().build();

    /**
     * Detailed validation breakdown.
     */
    @Data
    @Builder
    public static class ValidationDetails {
        
        // Coordinate validation
        private boolean coordinatesValid;
        private String coordinatesMessage;
        
        // Timestamp validation
        private boolean timestampValid;
        private String timestampMessage;
        private long minutesOld; // Age in minutes
        
        // Speed validation  
        private boolean speedValid;
        private String speedMessage;
        private BigDecimal speedThreshold;
        
        // Accuracy validation
        private boolean accuracyValid;
        private String accuracyMessage; 
        private String accuracyQuality;
        
        // Numeric validation
        private boolean numericValid;
        private String numericMessage;
        
        // Overall assessment
        private BigDecimal coordinatesPrecision; // Decimal places
        private boolean futureTimestamp;
        private boolean staleTimestamp; // > 30 minutes old
    }

    /**
     * Convenience method to check if GPS data has any errors.
     */
    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    /**
     * Convenience method to check if GPS data has any warnings.
     */
    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }

    /**
     * Check if GPS quality meets minimum threshold for tracking.
     * @param minimumScore Minimum acceptable quality score (0.0 to 1.0)
     * @return true if quality meets threshold
     */
    public boolean meetsQualityThreshold(BigDecimal minimumScore) {
        return qualityScore != null && 
               qualityScore.compareTo(minimumScore) >= 0;
    }

    /**
     * Get summary message for logging and error responses.
     */
    public String getSummaryMessage() {
        if (!isValid && !errors.isEmpty()) {
            return "GPS validation failed: " + String.join(", ", errors);
        }
        if (hasWarnings()) {
            return "GPS accepted with warnings: " + String.join(", ", warnings);
        }
        return "GPS validation passed - Quality: " + qualityLevel;
    }
}