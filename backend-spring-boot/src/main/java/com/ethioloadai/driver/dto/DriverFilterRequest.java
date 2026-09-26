package com.ethioloadai.driver.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Filter criteria for driver listing endpoint.
 *
 * <p>Frontend currently supports filtering by status. Additional filters
 * (location, availability, rating) can be added here if required.
 *
 * <p><strong>Status values:</strong>
 * <ul>
 *   <li><strong>active</strong> — verified AND active drivers
 *   <li><strong>approved</strong> — verified drivers (may include inactive)
 *   <li><strong>under_review</strong> — not verified but active
 *   <li><strong>submitted</strong> — alias for under_review (frontend compatibility)
 *   <li><strong>suspended</strong> — inactive drivers
 * </ul>
 *
 * <p>If status is null or empty, returns all drivers with role=DRIVER.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverFilterRequest {
    
    /**
     * Filter by driver status.
     * <p>Valid values: "active", "approved", "under_review", "submitted", "suspended"
     * <p>If null, returns all drivers.
     */
    private String status;
    
    /**
     * Filter by location/city (optional, not currently used by frontend).
     * <p>Performs case-insensitive substring match on User.location.
     */
    private String location;
    
    /**
     * Filter by minimum rating (optional, not currently used by frontend).
     * <p>Only returns drivers with average rating >= this value.
     */
    private Double minRating;
}

