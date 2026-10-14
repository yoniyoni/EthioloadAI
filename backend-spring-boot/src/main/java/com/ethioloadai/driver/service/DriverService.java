package com.ethioloadai.driver.service;

import com.ethioloadai.driver.dto.DriverFilterRequest;
import com.ethioloadai.driver.dto.DriverResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service for driver directory listing.
 *
 * <p>Provides read-only access to driver information for the public driver network.
 * Does NOT provide driver registration, modification, or KYC management — those
 * operations remain in AuthService and admin workflows.
 */
public interface DriverService {

    /**
     * List drivers with optional filtering and pagination.
     *
     * <p>Returns drivers with role=DRIVER, filtered by status if specified.
     * Computes derived fields (rating, totalDeliveries, successRate) from
     * related tables (Rating, Booking, Vehicle).
     *
     * @param filter Status and other filter criteria
     * @param pageable Pagination parameters (page, size, sort)
     * @return Paginated driver listing with computed statistics
     */
    Page<DriverResponse> listDrivers(DriverFilterRequest filter, Pageable pageable);
}

