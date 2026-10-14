package com.ethioloadai.carrier.fleet.service;

import com.ethioloadai.carrier.fleet.dto.*;
import com.ethioloadai.carrier.fleet.entity.DriverAvailability;
import com.ethioloadai.carrier.fleet.entity.VehicleAvailability;
import com.ethioloadai.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Phase 4 carrier fleet management service.
 * All operations are company-scoped; the {@code companyId} parameter is always
 * derived from the URL path and validated against the caller's active membership
 * by the controller before reaching this service.
 */
public interface CarrierFleetService {

    // ── Carrier Drivers ───────────────────────────────────────────────────────

    /** Add an existing DRIVER user to the company fleet. */
    CarrierDriverResponse addDriver(Long companyId, AddCarrierDriverRequest req, User caller);

    /** List company drivers, filtered by status. */
    Page<CarrierDriverResponse> listDrivers(Long companyId, String status, Pageable pageable);

    /** Get a single company driver by carrier_drivers.id. */
    CarrierDriverResponse getDriver(Long companyId, Long carrierDriverId);

    /** Update mutable fields (notes, manual availability). */
    CarrierDriverResponse updateDriver(Long companyId, Long carrierDriverId,
                                       UpdateCarrierDriverRequest req, User caller);

    /** Set driver status ACTIVE (re-activate). */
    CarrierDriverResponse activateDriver(Long companyId, Long carrierDriverId, User caller);

    /** Set driver status INACTIVE (suspend). */
    CarrierDriverResponse deactivateDriver(Long companyId, Long carrierDriverId, User caller);

    /** Set driver status REMOVED (soft-delete). Preserves assignment history. */
    CarrierDriverResponse removeDriver(Long companyId, Long carrierDriverId, User caller);

    // ── Company Vehicles ──────────────────────────────────────────────────────

    /** Register a new vehicle under the company. */
    CompanyVehicleResponse registerVehicle(Long companyId, RegisterCompanyVehicleRequest req, User caller);

    /** List company vehicles with pagination. */
    Page<CompanyVehicleResponse> listVehicles(Long companyId, Pageable pageable);

    /** Get a single company vehicle. */
    CompanyVehicleResponse getVehicle(Long companyId, Long vehicleId);

    /** Update mutable vehicle fields. */
    CompanyVehicleResponse updateVehicle(Long companyId, Long vehicleId,
                                         UpdateCompanyVehicleRequest req, User caller);

    /** Manually set vehicle availability (e.g. MAINTENANCE). */
    CompanyVehicleResponse setVehicleAvailability(Long companyId, Long vehicleId,
                                                  VehicleAvailability availability, User caller);

    // ── Driver ↔ Vehicle Assignments ──────────────────────────────────────────

    /** Assign a carrier driver to a vehicle. Ends any existing active assignment for driver/vehicle. */
    AssignmentResponse assign(Long companyId, CreateAssignmentRequest req, User caller);

    /** End an active assignment and return driver/vehicle to AVAILABLE. */
    AssignmentResponse endAssignment(Long companyId, Long assignmentId,
                                     EndAssignmentRequest req, User caller);

    /** Get a specific assignment (company-scoped). */
    AssignmentResponse getAssignment(Long companyId, Long assignmentId);

    /** Paginated assignment history for the company. */
    Page<AssignmentResponse> listAssignments(Long companyId, Pageable pageable);

    /** Assignment history for a specific driver. */
    List<AssignmentResponse> driverAssignmentHistory(Long companyId, Long carrierDriverId);

    /** Assignment history for a specific vehicle. */
    List<AssignmentResponse> vehicleAssignmentHistory(Long companyId, Long vehicleId);
}
