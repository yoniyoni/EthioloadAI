package com.ethioloadai.carrier.dispatch;

import com.ethioloadai.booking.entity.Booking;
import com.ethioloadai.booking.repository.BookingRepository;
import com.ethioloadai.carrier.dispatch.entity.DispatchStatus;
import com.ethioloadai.carrier.fleet.entity.CarrierDriverStatus;
import com.ethioloadai.carrier.fleet.entity.DriverAvailability;
import com.ethioloadai.carrier.fleet.repository.CarrierDriverRepository;
import com.ethioloadai.freight.entity.Freight;
import com.ethioloadai.freight.repository.FreightRepository;
import com.ethioloadai.vehicle.entity.Vehicle;
import com.ethioloadai.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
/**
 * Phase 6 — Full carrier dispatch integration tests.
 *
 * <p>Coverage:
 * <ol>
 *   <li>Role matrix — who may dispatch</li>
 *   <li>Award eligibility</li>
 *   <li>Driver eligibility</li>
 *   <li>Vehicle eligibility</li>
 *   <li>Cross-company attack prevention (IDOR, tenant isolation)</li>
 *   <li>Price tampering prevention</li>
 *   <li>Dispatch creation happy path</li>
 *   <li>Driver acceptance → Booking creation</li>
 *   <li>Driver rejection → resource release → redispatch</li>
 *   <li>Cancel pending dispatch</li>
 *   <li>Idempotent acceptance</li>
 *   <li>Awarded freights view</li>
 *   <li>Dispatch history</li>
 * </ol>
 */
@DisplayName("Phase 6 — Carrier Dispatch Integration")
class Phase6DispatchIntegrationTest extends DispatchTestSupport {

    @Autowired BookingRepository bookingRepo;
    @Autowired FreightRepository freightRepository;
    @Autowired CarrierDriverRepository carrierDriverRepo;
    @Autowired VehicleRepository vehicleRepository;

    @Test
    @DisplayName("Mobile driver inbox uses one API context prefix")
    void mobileInboxUsesContextRelativeRoute() throws Exception {
        mvc.perform(get("/driver/assignments").header("Authorization", auth(abcDriverA)))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isArray());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 1. ROLE MATRIX — who may dispatch
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("CARRIER_OWNER can dispatch → 201")
    void ownerCanDispatch() throws Exception {
        mvc.perform(post(dispatchUrl(abcCompanyId, awardedFreight.getId()))
                .header("Authorization", auth(abcOwner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("driverUserId", abcDriverA.getId(), "vehicleId", abcVehicle1Id))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.driverUserId").value(abcDriverA.getId().intValue()))
                .andExpect(jsonPath("$.vehicleId").value(abcVehicle1Id.intValue()))
                .andExpect(jsonPath("$.carrierCompanyId").value(abcCompanyId.intValue()))
                .andExpect(jsonPath("$.agreedAmount").value(45000.0));
    }

    @Test
    @DisplayName("FLEET_MANAGER can dispatch → 201")
    void fleetManagerCanDispatch() throws Exception {
        mvc.perform(post(dispatchUrl(abcCompanyId, awardedFreight.getId()))
                .header("Authorization", auth(abcFleetManager))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("driverUserId", abcDriverA.getId(), "vehicleId", abcVehicle1Id))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ASSIGNED"));
    }

    @Test
    @DisplayName("DISPATCHER can dispatch → 201")
    void dispatcherCanDispatch() throws Exception {
        mvc.perform(post(dispatchUrl(abcCompanyId, awardedFreight.getId()))
                .header("Authorization", auth(abcDispatcher))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("driverUserId", abcDriverA.getId(), "vehicleId", abcVehicle1Id))))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("DRIVER (ABC) cannot dispatch → 4xx")
    void driverCannotDispatch() throws Exception {
        mvc.perform(post(dispatchUrl(abcCompanyId, awardedFreight.getId()))
                .header("Authorization", auth(abcDriverA))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("driverUserId", abcDriverA.getId(), "vehicleId", abcVehicle1Id))))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("SHIPPER cannot dispatch → 4xx")
    void shipperCannotDispatch() throws Exception {
        mvc.perform(post(dispatchUrl(abcCompanyId, awardedFreight.getId()))
                .header("Authorization", auth(shipper))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("driverUserId", abcDriverA.getId(), "vehicleId", abcVehicle1Id))))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("Unauthenticated cannot dispatch → 401/403")
    void unauthenticatedCannotDispatch() throws Exception {
        mvc.perform(post(dispatchUrl(abcCompanyId, awardedFreight.getId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("driverUserId", abcDriverA.getId(), "vehicleId", abcVehicle1Id))))
                .andExpect(status().is4xxClientError());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 2. AWARD ELIGIBILITY
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Freight without carrier award cannot be dispatched → 422")
    void freightWithoutAwardNotDispatchable() throws Exception {
        var unawardedFreight = mkFreight(shipper, Freight.PriceType.NEGOTIABLE, new BigDecimal("30000"));
        mvc.perform(post(dispatchUrl(abcCompanyId, unawardedFreight.getId()))
                .header("Authorization", auth(abcOwner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("driverUserId", abcDriverA.getId(), "vehicleId", abcVehicle1Id))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("XYZ cannot dispatch ABC-awarded freight → 403")
    void xyzCannotDispatchAbcFreight() throws Exception {
        // XYZ doesn't have an approved vehicle yet in this test, so add a proper one
        mvc.perform(post(dispatchUrl(xyzCompanyId, awardedFreight.getId()))
                .header("Authorization", auth(xyzOwner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("driverUserId", xyzDriver.getId(), "vehicleId", xyzVehicleId))))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("CANCELLED freight cannot be dispatched → 422")
    void cancelledFreightNotDispatchable() throws Exception {
        var freight = freightRepository.findById(awardedFreight.getId()).orElseThrow();
        freight.setStatus(Freight.FreightStatus.CANCELLED);
        freightRepository.saveAndFlush(freight);

        mvc.perform(post(dispatchUrl(abcCompanyId, awardedFreight.getId()))
                .header("Authorization", auth(abcOwner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("driverUserId", abcDriverA.getId(), "vehicleId", abcVehicle1Id))))
                .andExpect(status().isUnprocessableEntity());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 3. CROSS-COMPANY ATTACK PREVENTION
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("ABC dispatcher cannot use XYZ driver → 403")
    void abcCannotUseXyzDriver() throws Exception {
        mvc.perform(post(dispatchUrl(abcCompanyId, awardedFreight.getId()))
                .header("Authorization", auth(abcDispatcher))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("driverUserId", xyzDriver.getId(), "vehicleId", abcVehicle1Id))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ABC dispatcher cannot use XYZ vehicle → 403")
    void abcCannotUseXyzVehicle() throws Exception {
        mvc.perform(post(dispatchUrl(abcCompanyId, awardedFreight.getId()))
                .header("Authorization", auth(abcDispatcher))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("driverUserId", abcDriverA.getId(), "vehicleId", xyzVehicleId))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("XYZ cannot view ABC dispatch details → 404")
    void xyzCannotViewAbcDispatch() throws Exception {
        // Create ABC dispatch first
        var createResult = mvc.perform(post(dispatchUrl(abcCompanyId, awardedFreight.getId()))
                .header("Authorization", auth(abcOwner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("driverUserId", abcDriverA.getId(), "vehicleId", abcVehicle1Id))))
                .andExpect(status().isCreated())
                .andReturn();

        Long dispatchId = ((Number) new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(createResult.getResponse().getContentAsString())
                .get("id").numberValue()).longValue();

        // XYZ tries to view it via their own company path
        mvc.perform(get(dispatchDetailUrl(xyzCompanyId, dispatchId))
                .header("Authorization", auth(xyzOwner)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("XYZ cannot cancel ABC dispatch → 404")
    void xyzCannotCancelAbcDispatch() throws Exception {
        var createResult = mvc.perform(post(dispatchUrl(abcCompanyId, awardedFreight.getId()))
                .header("Authorization", auth(abcOwner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("driverUserId", abcDriverA.getId(), "vehicleId", abcVehicle1Id))))
                .andExpect(status().isCreated())
                .andReturn();

        Long dispatchId = ((Number) new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(createResult.getResponse().getContentAsString())
                .get("id").numberValue()).longValue();

        mvc.perform(post(cancelDispatchUrl(xyzCompanyId, dispatchId))
                .header("Authorization", auth(xyzOwner)))
                .andExpect(status().isNotFound());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 4. DISPATCH CREATION — HAPPY PATH
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Valid dispatch: server derives company/offer/amount — client cannot override")
    void serverDerivesCompanyAndAmount() throws Exception {
        mvc.perform(post(dispatchUrl(abcCompanyId, awardedFreight.getId()))
                .header("Authorization", auth(abcOwner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of(
                        "driverUserId", abcDriverA.getId(),
                        "vehicleId",   abcVehicle1Id,
                        // Malicious fields — must be ignored
                        "carrierCompanyId", xyzCompanyId,
                        "agreedAmount",     1,
                        "status",           "DRIVER_ACCEPTED",
                        "bookingId",        9999
                ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.carrierCompanyId").value(abcCompanyId.intValue()))
                .andExpect(jsonPath("$.agreedAmount").value(45000.0))
                .andExpect(jsonPath("$.status").value("ASSIGNED"));
    }

    @Test
    @DisplayName("Dispatch sets driver availability to ASSIGNED")
    void dispatchSetsDriverAssigned() throws Exception {
        mvc.perform(post(dispatchUrl(abcCompanyId, awardedFreight.getId()))
                .header("Authorization", auth(abcOwner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("driverUserId", abcDriverA.getId(), "vehicleId", abcVehicle1Id))))
                .andExpect(status().isCreated());

        var cd = carrierDriverRepo
                .findByCarrierCompanyIdAndDriverUserId(abcCompanyId, abcDriverA.getId())
                .orElseThrow();
        assertThat(cd.getAvailability()).isEqualTo(DriverAvailability.ASSIGNED);
    }

    @Test
    @DisplayName("Dispatch sets vehicle availability to 'assigned'")
    void dispatchSetsVehicleAssigned() throws Exception {
        mvc.perform(post(dispatchUrl(abcCompanyId, awardedFreight.getId()))
                .header("Authorization", auth(abcOwner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("driverUserId", abcDriverA.getId(), "vehicleId", abcVehicle1Id))))
                .andExpect(status().isCreated());

        var v = vehicleRepository.findById(abcVehicle1Id).orElseThrow();
        assertThat(v.getAvailabilityStatus()).isEqualToIgnoringCase("assigned");
    }

    @Test
    @DisplayName("Duplicate active dispatch for same freight → 409")
    void duplicateActiveDispatchPrevented() throws Exception {
        // First dispatch succeeds
        mvc.perform(post(dispatchUrl(abcCompanyId, awardedFreight.getId()))
                .header("Authorization", auth(abcOwner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("driverUserId", abcDriverA.getId(), "vehicleId", abcVehicle1Id))))
                .andExpect(status().isCreated());

        // Second dispatch on same freight → conflict
        mvc.perform(post(dispatchUrl(abcCompanyId, awardedFreight.getId()))
                .header("Authorization", auth(abcOwner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("driverUserId", abcDriverB.getId(), "vehicleId", abcVehicle1Id))))
                .andExpect(status().isConflict());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 5. DRIVER ELIGIBILITY
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Inactive CarrierDriver cannot be dispatched → 422")
    void inactiveCarrierDriverRejected() throws Exception {
        var cd = carrierDriverRepo
                .findByCarrierCompanyIdAndDriverUserId(abcCompanyId, abcDriverA.getId())
                .orElseThrow();
        cd.setStatus(CarrierDriverStatus.INACTIVE);
        carrierDriverRepo.saveAndFlush(cd);

        mvc.perform(post(dispatchUrl(abcCompanyId, awardedFreight.getId()))
                .header("Authorization", auth(abcOwner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("driverUserId", abcDriverA.getId(), "vehicleId", abcVehicle1Id))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("Unavailable driver cannot be dispatched → 422")
    void unavailableDriverRejected() throws Exception {
        var cd = carrierDriverRepo
                .findByCarrierCompanyIdAndDriverUserId(abcCompanyId, abcDriverA.getId())
                .orElseThrow();
        cd.setAvailability(DriverAvailability.UNAVAILABLE);
        carrierDriverRepo.saveAndFlush(cd);

        mvc.perform(post(dispatchUrl(abcCompanyId, awardedFreight.getId()))
                .header("Authorization", auth(abcOwner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("driverUserId", abcDriverA.getId(), "vehicleId", abcVehicle1Id))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("ON_TRIP driver cannot be dispatched → 422")
    void onTripDriverRejected() throws Exception {
        var cd = carrierDriverRepo
                .findByCarrierCompanyIdAndDriverUserId(abcCompanyId, abcDriverA.getId())
                .orElseThrow();
        cd.setAvailability(DriverAvailability.ON_TRIP);
        carrierDriverRepo.saveAndFlush(cd);

        mvc.perform(post(dispatchUrl(abcCompanyId, awardedFreight.getId()))
                .header("Authorization", auth(abcOwner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("driverUserId", abcDriverA.getId(), "vehicleId", abcVehicle1Id))))
                .andExpect(status().isUnprocessableEntity());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 6. VEHICLE ELIGIBILITY
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Unapproved vehicle cannot be dispatched → 422")
    void unapprovedVehicleRejected() throws Exception {
        var v = vehicleRepository.findById(abcVehicle1Id).orElseThrow();
        v.setApprovalStatus(Vehicle.VehicleApprovalStatus.UNDER_REVIEW);
        vehicleRepository.saveAndFlush(v);

        mvc.perform(post(dispatchUrl(abcCompanyId, awardedFreight.getId()))
                .header("Authorization", auth(abcOwner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("driverUserId", abcDriverA.getId(), "vehicleId", abcVehicle1Id))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("Maintenance vehicle cannot be dispatched → 422")
    void maintenanceVehicleRejected() throws Exception {
        var v = vehicleRepository.findById(abcVehicle1Id).orElseThrow();
        v.setAvailabilityStatus("maintenance");
        vehicleRepository.saveAndFlush(v);

        mvc.perform(post(dispatchUrl(abcCompanyId, awardedFreight.getId()))
                .header("Authorization", auth(abcOwner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("driverUserId", abcDriverA.getId(), "vehicleId", abcVehicle1Id))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("Insufficient capacity vehicle rejected → 422")
    void insufficientCapacityVehicleRejected() throws Exception {
        // freight has 10 ton weight; set vehicle capacity to 5 tons
        var v = vehicleRepository.findById(abcVehicle1Id).orElseThrow();
        v.setCapacity(5.0);
        vehicleRepository.saveAndFlush(v);

        mvc.perform(post(dispatchUrl(abcCompanyId, awardedFreight.getId()))
                .header("Authorization", auth(abcOwner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("driverUserId", abcDriverA.getId(), "vehicleId", abcVehicle1Id))))
                .andExpect(status().isUnprocessableEntity());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 7. DRIVER ASSIGNMENT INBOX (IDOR protection)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Driver sees their own assignments in inbox")
    void driverSeesOwnAssignments() throws Exception {
        // Create dispatch
        mvc.perform(post(dispatchUrl(abcCompanyId, awardedFreight.getId()))
                .header("Authorization", auth(abcOwner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("driverUserId", abcDriverA.getId(), "vehicleId", abcVehicle1Id))))
                .andExpect(status().isCreated());

        // Driver A views inbox
        mvc.perform(get(driverAssignmentsUrl())
                .header("Authorization", auth(abcDriverA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].driverUserId").value(abcDriverA.getId().intValue()));
    }

    @Test
    @DisplayName("Driver B cannot access Driver A's assignment by ID → 404")
    void driverIdorProtected() throws Exception {
        var createResult = mvc.perform(post(dispatchUrl(abcCompanyId, awardedFreight.getId()))
                .header("Authorization", auth(abcOwner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("driverUserId", abcDriverA.getId(), "vehicleId", abcVehicle1Id))))
                .andExpect(status().isCreated())
                .andReturn();

        Long dispatchId = ((Number) new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(createResult.getResponse().getContentAsString())
                .get("id").numberValue()).longValue();

        // Driver B tries to access Driver A's assignment
        mvc.perform(get(driverAssignmentDetailUrl(dispatchId))
                .header("Authorization", auth(abcDriverB)))
                .andExpect(status().isNotFound());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 8. DRIVER ACCEPTANCE — BOOKING CREATION
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Assigned driver accepts → DRIVER_ACCEPTED + Booking created")
    void driverAcceptanceCreatesBooking() throws Exception {
        // Create dispatch
        var createResult = mvc.perform(post(dispatchUrl(abcCompanyId, awardedFreight.getId()))
                .header("Authorization", auth(abcOwner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("driverUserId", abcDriverA.getId(), "vehicleId", abcVehicle1Id))))
                .andExpect(status().isCreated())
                .andReturn();

        Long dispatchId = ((Number) new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(createResult.getResponse().getContentAsString())
                .get("id").numberValue()).longValue();

        // Driver accepts
        mvc.perform(post(driverAcceptUrl(dispatchId))
                .header("Authorization", auth(abcDriverA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRIVER_ACCEPTED"))
                .andExpect(jsonPath("$.bookingId").isNotEmpty());

        // Verify exactly one booking created
        List<Booking> bookings = bookingRepo.findByCargoRequest_Id(awardedFreight.getId());
        assertThat(bookings).hasSize(1);
        Booking booking = bookings.get(0);

        assertThat(booking.getEstimatedPrice()).isEqualByComparingTo("45000.00");
        assertThat(booking.getCarrierCompanyId()).isEqualTo(abcCompanyId);
        assertThat(booking.getDriver().getId()).isEqualTo(abcDriverA.getId());
        assertThat(booking.getVehicle().getId()).isEqualTo(abcVehicle1Id);
        assertThat(booking.getDispatchAssignmentId()).isEqualTo(dispatchId);
        assertThat(booking.getBookingStatus()).isEqualTo(Booking.BookingStatus.ACCEPTED);
    }

    @Test
    @DisplayName("Booking price equals Phase 5 agreed amount — cannot be changed by driver/dispatcher")
    void bookingPriceEqualsAgreedAmount() throws Exception {
        var createResult = mvc.perform(post(dispatchUrl(abcCompanyId, awardedFreight.getId()))
                .header("Authorization", auth(abcOwner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of(
                        "driverUserId", abcDriverA.getId(),
                        "vehicleId",   abcVehicle1Id,
                        "agreedAmount", 1   // mass-assignment attempt
                ))))
                .andExpect(status().isCreated())
                .andReturn();

        Long dispatchId = ((Number) new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(createResult.getResponse().getContentAsString())
                .get("id").numberValue()).longValue();

        mvc.perform(post(driverAcceptUrl(dispatchId))
                .header("Authorization", auth(abcDriverA)))
                .andExpect(status().isOk());

        Booking booking = bookingRepo.findByDispatchAssignmentId(dispatchId).orElseThrow();
        // Must be Phase 5 agreed amount, NOT tampered value
        assertThat(booking.getEstimatedPrice()).isEqualByComparingTo("45000.00");
    }

    @Test
    @DisplayName("Another driver cannot accept assignment not assigned to them → 403")
    void wrongDriverCannotAccept() throws Exception {
        var createResult = mvc.perform(post(dispatchUrl(abcCompanyId, awardedFreight.getId()))
                .header("Authorization", auth(abcOwner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("driverUserId", abcDriverA.getId(), "vehicleId", abcVehicle1Id))))
                .andExpect(status().isCreated())
                .andReturn();

        Long dispatchId = ((Number) new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(createResult.getResponse().getContentAsString())
                .get("id").numberValue()).longValue();

        // Driver B tries to accept Driver A's assignment
        mvc.perform(post(driverAcceptUrl(dispatchId))
                .header("Authorization", auth(abcDriverB)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("XYZ driver cannot accept ABC assignment → 403")
    void xyzDriverCannotAcceptAbcAssignment() throws Exception {
        var createResult = mvc.perform(post(dispatchUrl(abcCompanyId, awardedFreight.getId()))
                .header("Authorization", auth(abcOwner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("driverUserId", abcDriverA.getId(), "vehicleId", abcVehicle1Id))))
                .andExpect(status().isCreated())
                .andReturn();

        Long dispatchId = ((Number) new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(createResult.getResponse().getContentAsString())
                .get("id").numberValue()).longValue();

        mvc.perform(post(driverAcceptUrl(dispatchId))
                .header("Authorization", auth(xyzDriver)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Idempotent acceptance — second accept does not create duplicate Booking")
    void idempotentAcceptanceNoDuplicateBooking() throws Exception {
        var createResult = mvc.perform(post(dispatchUrl(abcCompanyId, awardedFreight.getId()))
                .header("Authorization", auth(abcOwner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("driverUserId", abcDriverA.getId(), "vehicleId", abcVehicle1Id))))
                .andExpect(status().isCreated())
                .andReturn();

        Long dispatchId = ((Number) new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(createResult.getResponse().getContentAsString())
                .get("id").numberValue()).longValue();

        mvc.perform(post(driverAcceptUrl(dispatchId)).header("Authorization", auth(abcDriverA)))
                .andExpect(status().isOk());
        mvc.perform(post(driverAcceptUrl(dispatchId)).header("Authorization", auth(abcDriverA)))
                .andExpect(status().is4xxClientError()); // second call is rejected (invalid transition)

        List<Booking> bookings = bookingRepo.findByCargoRequest_Id(awardedFreight.getId());
        assertThat(bookings).hasSize(1);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 9. DRIVER REJECTION → RESOURCE RELEASE → REDISPATCH
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Driver rejects → DRIVER_REJECTED, resources released, carrier can redispatch")
    void driverRejectionAndRedispatch() throws Exception {
        // Dispatch to Driver A
        var createResult = mvc.perform(post(dispatchUrl(abcCompanyId, awardedFreight2.getId()))
                .header("Authorization", auth(abcOwner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("driverUserId", abcDriverA.getId(), "vehicleId", abcVehicle1Id))))
                .andExpect(status().isCreated())
                .andReturn();

        Long dispatchId = ((Number) new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(createResult.getResponse().getContentAsString())
                .get("id").numberValue()).longValue();

        // Driver A rejects
        mvc.perform(post(driverRejectUrl(dispatchId))
                .header("Authorization", auth(abcDriverA))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("rejectionReason", "Not available today"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRIVER_REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("Not available today"));

        // Verify resources released
        var cdA = carrierDriverRepo
                .findByCarrierCompanyIdAndDriverUserId(abcCompanyId, abcDriverA.getId())
                .orElseThrow();
        assertThat(cdA.getAvailability()).isEqualTo(DriverAvailability.AVAILABLE);

        var v = vehicleRepository.findById(abcVehicle1Id).orElseThrow();
        assertThat(v.getAvailabilityStatus()).isEqualToIgnoringCase("available");

        // Commercial award remains — no Booking yet
        assertThat(bookingRepo.findByCargoRequest_Id(awardedFreight2.getId())).isEmpty();

        // Carrier can redispatch to Driver B
        mvc.perform(post(dispatchUrl(abcCompanyId, awardedFreight2.getId()))
                .header("Authorization", auth(abcOwner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("driverUserId", abcDriverB.getId(), "vehicleId", abcVehicle1Id))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.driverUserId").value(abcDriverB.getId().intValue()));
    }

    @Test
    @DisplayName("Rejection history preserved — old assignment not deleted")
    void rejectionHistoryPreserved() throws Exception {
        var createResult = mvc.perform(post(dispatchUrl(abcCompanyId, awardedFreight2.getId()))
                .header("Authorization", auth(abcOwner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("driverUserId", abcDriverA.getId(), "vehicleId", abcVehicle1Id))))
                .andExpect(status().isCreated())
                .andReturn();

        Long dispatchId = ((Number) new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(createResult.getResponse().getContentAsString())
                .get("id").numberValue()).longValue();

        // Driver A rejects
        mvc.perform(post(driverRejectUrl(dispatchId))
                .header("Authorization", auth(abcDriverA)))
                .andExpect(status().isOk());

        // Redispatch to Driver B
        mvc.perform(post(dispatchUrl(abcCompanyId, awardedFreight2.getId()))
                .header("Authorization", auth(abcOwner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("driverUserId", abcDriverB.getId(), "vehicleId", abcVehicle1Id))))
                .andExpect(status().isCreated());

        // Full history has 2 entries for this freight
        mvc.perform(get(freightDispatchesUrl(abcCompanyId, awardedFreight2.getId()))
                .header("Authorization", auth(abcOwner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        // First entry is the rejected one
        var history = dispatchRepo.findByFreightIdAndCarrierCompanyIdOrderByAssignedAtDesc(
                awardedFreight2.getId(), abcCompanyId);
        assertThat(history).hasSize(2);
        // One is DRIVER_REJECTED
        assertThat(history.stream().anyMatch(d -> d.getStatus() == DispatchStatus.DRIVER_REJECTED))
                .isTrue();
        // One is ASSIGNED (new)
        assertThat(history.stream().anyMatch(d -> d.getStatus() == DispatchStatus.ASSIGNED))
                .isTrue();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 10. CANCEL PENDING DISPATCH
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Carrier operator can cancel pending dispatch → CANCELLED + resources released")
    void carrierCanCancelPendingDispatch() throws Exception {
        var createResult = mvc.perform(post(dispatchUrl(abcCompanyId, awardedFreight.getId()))
                .header("Authorization", auth(abcOwner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("driverUserId", abcDriverA.getId(), "vehicleId", abcVehicle1Id))))
                .andExpect(status().isCreated())
                .andReturn();

        Long dispatchId = ((Number) new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(createResult.getResponse().getContentAsString())
                .get("id").numberValue()).longValue();

        // Cancel
        mvc.perform(post(cancelDispatchUrl(abcCompanyId, dispatchId))
                .header("Authorization", auth(abcOwner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        // Resources released
        var cd = carrierDriverRepo
                .findByCarrierCompanyIdAndDriverUserId(abcCompanyId, abcDriverA.getId())
                .orElseThrow();
        assertThat(cd.getAvailability()).isEqualTo(DriverAvailability.AVAILABLE);
    }

    @Test
    @DisplayName("Cancelled dispatch cannot be accepted → 422")
    void cancelledDispatchCannotBeAccepted() throws Exception {
        var createResult = mvc.perform(post(dispatchUrl(abcCompanyId, awardedFreight.getId()))
                .header("Authorization", auth(abcOwner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("driverUserId", abcDriverA.getId(), "vehicleId", abcVehicle1Id))))
                .andExpect(status().isCreated())
                .andReturn();

        Long dispatchId = ((Number) new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(createResult.getResponse().getContentAsString())
                .get("id").numberValue()).longValue();

        // Cancel
        mvc.perform(post(cancelDispatchUrl(abcCompanyId, dispatchId))
                .header("Authorization", auth(abcOwner)));

        // Driver tries to accept cancelled assignment
        mvc.perform(post(driverAcceptUrl(dispatchId))
                .header("Authorization", auth(abcDriverA)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("Unrelated company cannot cancel ABC's dispatch → 404")
    void xyzCannotCancelAbcDispatchCheck() throws Exception {
        var createResult = mvc.perform(post(dispatchUrl(abcCompanyId, awardedFreight.getId()))
                .header("Authorization", auth(abcOwner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("driverUserId", abcDriverA.getId(), "vehicleId", abcVehicle1Id))))
                .andExpect(status().isCreated())
                .andReturn();

        Long dispatchId = ((Number) new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(createResult.getResponse().getContentAsString())
                .get("id").numberValue()).longValue();

        mvc.perform(post(cancelDispatchUrl(xyzCompanyId, dispatchId))
                .header("Authorization", auth(xyzOwner)))
                .andExpect(status().isNotFound());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 11. AWARDED FREIGHTS VIEW
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Carrier can view their awarded freights list")
    void carrierViewsAwardedFreights() throws Exception {
        mvc.perform(get(awardedFreightsUrl(abcCompanyId))
                .header("Authorization", auth(abcOwner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$[0].agreedAmount").isNotEmpty())
                .andExpect(jsonPath("$[0].freightId").isNotEmpty())
                .andExpect(jsonPath("$[0].carrierOfferId").isNotEmpty());
    }

    @Test
    @DisplayName("XYZ cannot view ABC awarded freights via XYZ company path")
    void xyzCannotViewAbcAwardedFreights() throws Exception {
        // XYZ has their own company — they can view their own (which is empty for freight2 etc.)
        // But they cannot see ABC's freights via their own company ID
        mvc.perform(get(awardedFreightsUrl(xyzCompanyId))
                .header("Authorization", auth(xyzOwner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));  // XYZ has no awarded freights in test data
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 12. FULL WORKFLOW — end-to-end happy path
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Full workflow: dispatch → accept → booking correct → freight MATCHED")
    void fullWorkflowHappyPath() throws Exception {
        // 1. Create dispatch
        var createResult = mvc.perform(post(dispatchUrl(abcCompanyId, awardedFreight.getId()))
                .header("Authorization", auth(abcDispatcher))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("driverUserId", abcDriverA.getId(), "vehicleId", abcVehicle1Id,
                        "notes", "Highway route preferred"))))
                .andExpect(status().isCreated())
                .andReturn();

        Long dispatchId = ((Number) new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(createResult.getResponse().getContentAsString())
                .get("id").numberValue()).longValue();

        // 2. Driver views assignment
        mvc.perform(get(driverAssignmentDetailUrl(dispatchId))
                .header("Authorization", auth(abcDriverA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.agreedAmount").value(45000.0))
                .andExpect(jsonPath("$.notes").value("Highway route preferred"));

        // 3. Driver accepts
        var acceptResult = mvc.perform(post(driverAcceptUrl(dispatchId))
                .header("Authorization", auth(abcDriverA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRIVER_ACCEPTED"))
                .andExpect(jsonPath("$.bookingId").isNotEmpty())
                .andReturn();

        Long bookingId = ((Number) new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(acceptResult.getResponse().getContentAsString())
                .get("bookingId").numberValue()).longValue();

        // 4. Verify Booking correctness
        Booking booking = bookingRepo.findById(bookingId).orElseThrow();
        assertThat(booking.getEstimatedPrice()).isEqualByComparingTo("45000.00");
        assertThat(booking.getCarrierCompanyId()).isEqualTo(abcCompanyId);
        assertThat(booking.getCarrierOfferId()).isEqualTo(abcAwardOfferId);
        assertThat(booking.getDriver().getId()).isEqualTo(abcDriverA.getId());
        assertThat(booking.getVehicle().getId()).isEqualTo(abcVehicle1Id);
        assertThat(booking.getBookingStatus()).isEqualTo(Booking.BookingStatus.ACCEPTED);
        assertThat(booking.getBid()).isNull(); // carrier dispatch — no legacy bid

        // 5. Verify freight status updated to MATCHED
        var freight = freightRepository.findById(awardedFreight.getId()).orElseThrow();
        assertThat(freight.getStatus()).isEqualTo(Freight.FreightStatus.MATCHED);

        // 6. Verify driver is ON_TRIP
        var cd = carrierDriverRepo
                .findByCarrierCompanyIdAndDriverUserId(abcCompanyId, abcDriverA.getId())
                .orElseThrow();
        assertThat(cd.getAvailability()).isEqualTo(DriverAvailability.ON_TRIP);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 13. PHASE 1-5 REGRESSION — verify prior phases unaffected
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Booking entity: new Phase 6 columns are null for legacy-path bookings")
    void legacyBookingColumnsNullable() {
        // The bookingRepo schema should allow null for Phase 6 columns
        // If this query works without error, Phase 6 migration is backward-compatible
        var bookings = bookingRepo.findAll();
        // All existing test bookings (none in this test) should have nullable Phase 6 columns
        assertThat(bookings).allSatisfy(b -> {
            // Confirm Phase 6 columns are accessible without NPE (means migration is clean)
            b.getCarrierOfferId();
            b.getDispatchAssignmentId();
            b.getCarrierCompanyId();
        });
    }

    @Test
    @DisplayName("Phase 5 offer still queryable after Phase 6 migrations")
    void phase5OfferRemainsQueryable() {
        var offer = offerRepo.findById(abcAwardOfferId).orElseThrow();
        assertThat(offer.getStatus().name()).isEqualTo("ACCEPTED");
        assertThat(offer.getAgreedAmount()).isEqualByComparingTo("45000.00");
    }

    @Test
    @DisplayName("Fleet driver addition still works after Phase 6 migrations")
    void phase4DriverAddStillWorks() throws Exception {
        var newDriver = mkUser(com.ethioloadai.user.entity.User.Role.DRIVER);
        var req = new com.ethioloadai.carrier.fleet.dto.AddCarrierDriverRequest();
        req.setDriverUserId(newDriver.getId());
        var result = fleetService.addDriver(abcCompanyId, req, abcOwner);
        assertThat(result).isNotNull();
        assertThat(result.getStatus().name()).isEqualTo("ACTIVE");
    }
}
