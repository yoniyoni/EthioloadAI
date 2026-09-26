package com.ethioloadai.driver.dto;

import com.ethioloadai.vehicle.dto.VehicleResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Safe DTO for public driver directory listing.
 *
 * <p>Exposes only information appropriate for shippers/admins browsing
 * the driver network. Does NOT expose:
 * <ul>
 *   <li>password or authentication credentials
 *   <li>KYC document contents or file paths
 *   <li>internal security fields (rememberToken)
 *   <li>fleet_owner_id (internal relationship)
 * </ul>
 *
 * <p><strong>Field mapping notes:</strong>
 * <ul>
 *   <li><strong>rating:</strong> Computed from {@code Rating} table (average rating received by this driver)
 *   <li><strong>totalDeliveries:</strong> Count of completed bookings where this user was the driver
 *   <li><strong>successRate:</strong> Percentage of completed vs. all bookings (completed / total * 100)
 *   <li><strong>yearsExperience:</strong> NOT in current schema — will return 0 or null until field added
 *   <li><strong>isAvailable:</strong> NOT in current schema — derived from vehicle availability or null
 *   <li><strong>status:</strong> Maps to {@code User.verificationStatus} and {@code User.isActive}:
 *     <ul>
 *       <li>"active" = verified + active
 *       <li>"approved" = verified but possibly inactive
 *       <li>"under_review" = not verified but active
 *       <li>"suspended" = not active
 *     </ul>
 *   <li><strong>vehicles:</strong> List of vehicles where {@code Vehicle.userId == driver.id}
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverResponse {
    
    /** Driver ID (User.id) */
    private Long id;
    
    /** Driver's full name */
    private String fullName;
    
    /** Driver's phone number */
    private String phone;
    
    /** Driver's email (may be null) */
    private String email;
    
    /** Driver's location/city */
    private String location;
    
    /**
     * Derived status for frontend filtering.
     * <p>Values: "active", "approved", "under_review", "suspended"
     */
    private String status;
    
    /**
     * Average rating received (1.0-5.0).
     * <p>Computed from Rating table where driver_id = this driver.
     * <p>Null or 0.0 if no ratings exist.
     */
    private Double rating;
    
    /**
     * Total completed deliveries.
     * <p>Count of bookings with status=COMPLETED where driver_id = this driver.
     */
    private Integer totalDeliveries;
    
    /**
     * Years of driving experience.
     * <p><strong>NOT IMPLEMENTED:</strong> No such field exists in current User schema.
     * <p>Returns 0 until database schema is extended.
     */
    private Integer yearsExperience;
    
    /**
     * Success rate percentage (0-100).
     * <p>Formula: (completed bookings / total bookings) * 100
     * <p>Null if no bookings exist.
     */
    private Double successRate;
    
    /**
     * Driver availability flag.
     * <p><strong>NOT IMPLEMENTED:</strong> No global availability flag in User.
     * <p>Currently derived from whether driver has any available vehicles.
     * <p>Returns true if ANY vehicle.availabilityStatus = 'available', else false.
     */
    private Boolean isAvailable;
    
    /**
     * Driver's registered vehicles.
     * <p>List of vehicles where vehicle.userId = this driver's ID.
     * <p>Empty list if driver has no vehicles.
     */
    private List<VehicleResponse> vehicles;
}

