package com.ethioloadai.carrier.dispatch.service;

import com.ethioloadai.carrier.dispatch.dto.*;
import com.ethioloadai.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Phase 6 — Carrier internal dispatch service.
 *
 * <p>Handles the full lifecycle of a DispatchAssignment:
 * <ol>
 *   <li>Winning-carrier validation</li>
 *   <li>Driver + vehicle eligibility</li>
 *   <li>Dispatch creation</li>
 *   <li>Driver notification</li>
 *   <li>Driver acceptance → Booking creation</li>
 *   <li>Driver rejection → resource release → redispatch</li>
 *   <li>Carrier-operator cancellation</li>
 * </ol>
 */
public interface DispatchService {

    // ── Carrier-facing operations ─────────────────────────────────────────────

    /**
     * Create a dispatch assignment for an awarded freight.
     *
     * @param freightId  The freight to dispatch (must have a winning CarrierOffer)
     * @param companyId  The requesting company (validated against the award)
     * @param request    Driver and vehicle selection
     * @param caller     Authenticated CARRIER_OWNER / FLEET_MANAGER / DISPATCHER
     * @return The created dispatch assignment
     */
    DispatchAssignmentResponse createDispatch(Long freightId, Long companyId,
                                              CreateDispatchRequest request, User caller);

    /**
     * Cancel a pending (ASSIGNED) dispatch assignment.
     * Only the company's owner/fleet manager/dispatcher may cancel.
     *
     * @param dispatchId The assignment to cancel
     * @param companyId  The requesting company (scoped access)
     * @param caller     Authenticated carrier operator
     */
    DispatchAssignmentResponse cancelDispatch(Long dispatchId, Long companyId, User caller);

    /**
     * Get a single dispatch assignment (company-scoped).
     */
    DispatchAssignmentResponse getDispatch(Long dispatchId, Long companyId, User caller);

    /**
     * Paginated list of all dispatch assignments for a company.
     */
    Page<DispatchAssignmentResponse> listCompanyDispatches(Long companyId, User caller, Pageable pageable);

    /**
     * List all dispatch assignments for a specific awarded freight within a company.
     */
    List<DispatchAssignmentResponse> listFreightDispatches(Long freightId, Long companyId, User caller);

    /**
     * Awarded freights for a carrier company, with active dispatch summary.
     * Used by the carrier dispatch portal.
     */
    List<AwardedFreightSummary> getAwardedFreights(Long companyId, User caller);

    // ── Driver-facing operations ──────────────────────────────────────────────

    /**
     * Get all assignments for the authenticated driver (their inbox).
     */
    List<DispatchAssignmentResponse> getMyAssignments(User driver);

    /**
     * Get a specific assignment for the authenticated driver.
     * Returns 404 (not 403) if the assignment belongs to another driver.
     */
    DispatchAssignmentResponse getMyAssignment(Long dispatchId, User driver);

    /**
     * Driver accepts an assignment.
     * Atomically creates a Booking and transitions the assignment to DRIVER_ACCEPTED.
     *
     * @param dispatchId The assignment to accept
     * @param driver     The authenticated DRIVER user
     * @return The updated dispatch (with bookingId set)
     */
    DispatchAssignmentResponse acceptAssignment(Long dispatchId, User driver);

    /**
     * Driver rejects an assignment.
     * Releases driver + vehicle reservations. Carrier may redispatch.
     *
     * @param dispatchId The assignment to reject
     * @param request    Optional rejection reason
     * @param driver     The authenticated DRIVER user
     */
    DispatchAssignmentResponse rejectAssignment(Long dispatchId, DriverRespondRequest request, User driver);
}
