package com.ethioloadai.carrier.dispatch.service;

import com.ethioloadai.bid.service.CommissionService;
import com.ethioloadai.booking.entity.Booking;
import com.ethioloadai.booking.repository.BookingRepository;
import com.ethioloadai.carrier.company.entity.CarrierCompany;
import com.ethioloadai.carrier.company.repository.CarrierCompanyRepository;
import com.ethioloadai.carrier.dispatch.dto.*;
import com.ethioloadai.carrier.dispatch.entity.DispatchAssignment;
import com.ethioloadai.carrier.dispatch.entity.DispatchStatus;
import com.ethioloadai.carrier.dispatch.exception.DispatchException;
import com.ethioloadai.carrier.dispatch.repository.DispatchAssignmentRepository;
import com.ethioloadai.carrier.fleet.entity.CarrierDriver;
import com.ethioloadai.carrier.fleet.entity.CarrierDriverStatus;
import com.ethioloadai.carrier.fleet.entity.DriverAvailability;
import com.ethioloadai.carrier.fleet.repository.CarrierDriverRepository;
import com.ethioloadai.carrier.offer.entity.CarrierOffer;
import com.ethioloadai.carrier.offer.entity.CarrierAssignmentStatus;
import com.ethioloadai.carrier.offer.entity.CarrierOffer.OfferStatus;
import com.ethioloadai.carrier.offer.repository.CarrierOfferRepository;
import com.ethioloadai.carrier.team.entity.CompanyRole;
import com.ethioloadai.carrier.team.service.CompanyContext;
import com.ethioloadai.carrier.team.service.CompanyContextService;
import com.ethioloadai.freight.entity.Freight;
import com.ethioloadai.freight.repository.FreightRepository;
import com.ethioloadai.notification.service.NotificationStoreService;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.user.repository.UserRepository;
import com.ethioloadai.vehicle.entity.Vehicle;
import com.ethioloadai.vehicle.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Phase 6 — Carrier internal dispatch service implementation.
 *
 * <h2>Security model</h2>
 * <ul>
 *   <li>Company identity is always resolved from JWT → active membership → CarrierCompany.
 *       It is NEVER accepted from the request payload.</li>
 *   <li>All resource ownership (driver, vehicle) is validated against the winning
 *       company BEFORE any state mutation.</li>
 *   <li>The agreed commercial price comes from CarrierOffer.agreedAmount and is
 *       copied immutably to DispatchAssignment. The driver/dispatcher cannot change it.</li>
 * </ul>
 *
 * <h2>Concurrency strategy</h2>
 * <ul>
 *   <li>Pessimistic write lock on the freight's active dispatch row prevents
 *       two concurrent dispatches from both succeeding.</li>
 *   <li>@Version on DispatchAssignment prevents concurrent accept+cancel race.</li>
 *   <li>Unique partial index on bookings(dispatch_assignment_id) prevents duplicate bookings.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class DispatchServiceImpl implements DispatchService {

    private static final Logger log = LoggerFactory.getLogger(DispatchServiceImpl.class);

    private final DispatchAssignmentRepository dispatchRepo;
    private final FreightRepository freightRepo;
    private final CarrierOfferRepository offerRepo;
    private final CarrierCompanyRepository companyRepo;
    private final CarrierDriverRepository driverRepo;
    private final VehicleRepository vehicleRepo;
    private final UserRepository userRepo;
    private final BookingRepository bookingRepo;
    private final CompanyContextService companyContextService;
    private final NotificationStoreService notificationService;
    private final CommissionService commissionService;

    // ════════════════════════════════════════════════════════════════════════════
    // Carrier-facing operations
    // ════════════════════════════════════════════════════════════════════════════

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public DispatchAssignmentResponse createDispatch(Long freightId, Long companyId,
                                                     CreateDispatchRequest request, User caller) {
        log.info("createDispatch: freight={} company={} driver={} vehicle={} by={}",
                freightId, companyId, request.getDriverUserId(), request.getVehicleId(), caller.getId());

        // 1. Resolve and validate company context + role
        CompanyContext ctx = companyContextService.resolveForCompany(companyId, caller);
        requireDispatchRole(ctx);

        // 2. Load and validate freight
        Freight freight = freightRepo.findByIdForUpdate(freightId)
                .orElseThrow(() -> DispatchException.freightNotDispatchable(freightId, "freight not found"));
        validateFreightDispatchable(freight);

        // 3. Validate winning carrier award
        Long awardOfferId = freight.getCarrierAwardOfferId();
        if (awardOfferId == null) {
            throw DispatchException.noAward(freightId);
        }
        CarrierOffer award = offerRepo.findById(awardOfferId)
                .orElseThrow(() -> DispatchException.noAward(freightId));
        if (award.getStatus() != OfferStatus.ACCEPTED) {
            throw DispatchException.noAward(freightId);
        }
        // 4. Verify THIS company won the award (cross-company attack prevention)
        if (!award.getCarrierCompany().getId().equals(companyId)) {
            throw DispatchException.notAwardedCompany(freightId);
        }

        // 5. Validate agreed amount is frozen
        BigDecimal agreedAmount = award.getAgreedAmount();
        if (agreedAmount == null || agreedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw DispatchException.agreedAmountMissing(awardOfferId);
        }

        // 6. Check no active dispatch already exists (pessimistic lock prevents race)
        Optional<DispatchAssignment> existingActive = dispatchRepo.findActiveByFreightIdForUpdate(freightId);
        if (existingActive.isPresent()) {
            DispatchAssignment existing = existingActive.get();
            if (existing.getCarrierOfferId().equals(awardOfferId)
                    && existing.getCarrierCompanyId().equals(companyId)
                    && existing.getDriverUserId().equals(request.getDriverUserId())
                    && existing.getVehicleId().equals(request.getVehicleId())) {
                return DispatchAssignmentResponse.from(existing);
            }
            throw DispatchException.activeDispatchExists(freightId);
        }
        if (freight.getStatus() != Freight.FreightStatus.POSTED) {
            throw DispatchException.freightNotDispatchable(freightId,
                    "freight must remain POSTED before dispatch creation");
        }

        // 7. Validate driver eligibility (cross-company check included)
        CarrierDriver carrierDriver = validateDriver(request.getDriverUserId(), companyId);

        // 8. Validate vehicle eligibility (cross-company check included)
        Vehicle vehicle = validateVehicle(request.getVehicleId(), companyId, freight);

        // 9. Check driver not already pending another dispatch
        dispatchRepo.findPendingByDriverUserId(request.getDriverUserId()).ifPresent(d -> {
            throw DispatchException.driverIneligible(
                    "driver already has an active pending dispatch (id=" + d.getId() + ")");
        });

        // 10. Check vehicle not already pending another dispatch
        dispatchRepo.findPendingByVehicleId(request.getVehicleId()).ifPresent(d -> {
            throw DispatchException.vehicleIneligible(
                    "vehicle already has a pending dispatch (id=" + d.getId() + ")");
        });

        // 11. Create the dispatch assignment atomically
        DispatchAssignment dispatch = DispatchAssignment.builder()
                .freightId(freightId)
                .carrierOfferId(awardOfferId)
                .carrierCompanyId(companyId)
                .driverUserId(request.getDriverUserId())
                .vehicleId(request.getVehicleId())
                .assignedBy(caller.getId())
                .status(DispatchStatus.ASSIGNED)
                .agreedAmount(agreedAmount)
                .notes(request.getNotes() != null ? request.getNotes().strip() : null)
                .assignedAt(LocalDateTime.now())
                .build();

        try {
            dispatch = dispatchRepo.saveAndFlush(dispatch);
        } catch (DataIntegrityViolationException ex) {
            // Unique partial index fired — concurrent dispatch created simultaneously
            throw DispatchException.activeDispatchExists(freightId);
        }
        award.setAssignmentStatus(CarrierAssignmentStatus.DISPATCH_PENDING);
        offerRepo.save(award);

        // 12. Mark driver ASSIGNED (system-managed availability)
        if (carrierDriver.getAvailability() == DriverAvailability.AVAILABLE) {
            carrierDriver.setAvailability(DriverAvailability.ASSIGNED);
            driverRepo.save(carrierDriver);
        }

        // 13. Mark vehicle availability as 'assigned' (matches existing string convention)
        if ("available".equalsIgnoreCase(vehicle.getAvailabilityStatus())) {
            vehicle.setAvailabilityStatus("assigned");
            vehicleRepo.save(vehicle);
        }

        // 14. Notify the driver (non-blocking — notification failure must not corrupt state)
        notifyDriver(request.getDriverUserId(), freight, dispatch, award.getCarrierCompany());

        log.info("createDispatch: created dispatchId={} freight={} company={} driver={} vehicle={} amount={}",
                dispatch.getId(), freightId, companyId,
                request.getDriverUserId(), request.getVehicleId(), agreedAmount);

        return DispatchAssignmentResponse.from(dispatch);
    }

    @Override
    @Transactional
    public DispatchAssignmentResponse cancelDispatch(Long dispatchId, Long companyId, User caller) {
        log.info("cancelDispatch: dispatchId={} company={} by={}", dispatchId, companyId, caller.getId());

        CompanyContext ctx = companyContextService.resolveForCompany(companyId, caller);
        requireDispatchRole(ctx);

        Long freightId = dispatchRepo.findFreightIdById(dispatchId)
                .orElseThrow(() -> DispatchException.notFound(dispatchId));
        freightRepo.findByIdForUpdate(freightId)
                .orElseThrow(() -> DispatchException.freightNotDispatchable(
                        freightId, "freight not found"));
        DispatchAssignment dispatch = dispatchRepo.findByIdForUpdate(dispatchId)
                .orElseThrow(() -> DispatchException.notFound(dispatchId));

        // Verify company ownership
        if (!dispatch.getCarrierCompanyId().equals(companyId)) {
            throw DispatchException.notFound(dispatchId); // 404 — don't reveal existence
        }

        if (dispatch.getStatus() != DispatchStatus.ASSIGNED) {
            throw DispatchException.invalidTransition(dispatch.getStatus(), "cancel");
        }

        dispatch.setStatus(DispatchStatus.CANCELLED);
        dispatch.setCancelledAt(LocalDateTime.now());
        dispatch = dispatchRepo.save(dispatch);
        updateAwardAssignmentStatus(dispatch.getCarrierOfferId(),
                CarrierAssignmentStatus.PENDING_ASSIGNMENT);

        // Release driver and vehicle
        releaseDriverAndVehicle(dispatch);

        log.info("cancelDispatch: dispatchId={} cancelled by={}", dispatchId, caller.getId());
        return DispatchAssignmentResponse.from(dispatch);
    }

    @Override
    @Transactional(readOnly = true)
    public DispatchAssignmentResponse getDispatch(Long dispatchId, Long companyId, User caller) {
        companyContextService.resolveForCompany(companyId, caller); // membership check
        DispatchAssignment d = dispatchRepo.findByIdAndCarrierCompanyId(dispatchId, companyId)
                .orElseThrow(() -> DispatchException.notFound(dispatchId));
        return DispatchAssignmentResponse.from(d);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<DispatchAssignmentResponse> listCompanyDispatches(Long companyId, User caller, Pageable pageable) {
        companyContextService.resolveForCompany(companyId, caller); // membership check
        return dispatchRepo.findByCarrierCompanyIdOrderByAssignedAtDesc(companyId, pageable)
                .map(DispatchAssignmentResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DispatchAssignmentResponse> listFreightDispatches(Long freightId, Long companyId, User caller) {
        companyContextService.resolveForCompany(companyId, caller); // membership check
        return dispatchRepo.findByFreightIdAndCarrierCompanyIdOrderByAssignedAtDesc(freightId, companyId)
                .stream().map(DispatchAssignmentResponse::from).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AwardedFreightSummary> getAwardedFreights(Long companyId, User caller) {
        companyContextService.resolveForCompany(companyId, caller); // membership check

        // Load all accepted offers for this company
        List<CarrierOffer> acceptedOffers = offerRepo.findAcceptedByCompanyId(companyId);

        return acceptedOffers.stream().map(offer -> {
            Freight freight = offer.getFreight();

            // Find active dispatch for this freight (if any)
            Optional<DispatchAssignment> activeDispatch =
                    dispatchRepo.findActiveByFreightId(freight.getId());

            AwardedFreightSummary.DispatchSummary dispatchSummary = activeDispatch.map(d ->
                    AwardedFreightSummary.DispatchSummary.builder()
                            .dispatchId(d.getId())
                            .status(d.getStatus().name())
                            .driverUserId(d.getDriverUserId())
                            .vehicleId(d.getVehicleId())
                            .assignedAt(d.getAssignedAt())
                            .bookingId(d.getBookingId())
                            .build()
            ).orElse(null);

            return AwardedFreightSummary.builder()
                    .freightId(freight.getId())
                    .pickupLocation(freight.getPickupLocation())
                    .destination(freight.getDestination())
                    .materialType(freight.getMaterialType())
                    .weightTons(freight.getWeightTons())
                    .agreedAmount(offer.getAgreedAmount())
                    .carrierOfferId(offer.getId())
                    .activeDispatch(dispatchSummary)
                    .awardedAt(offer.getAcceptedAt())
                    .build();
        }).collect(Collectors.toList());
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Driver-facing operations
    // ════════════════════════════════════════════════════════════════════════════

    @Override
    @Transactional(readOnly = true)
    public List<DispatchAssignmentResponse> getMyAssignments(User driver) {
        return dispatchRepo.findByDriverUserIdOrderByAssignedAtDesc(driver.getId())
                .stream().map(DispatchAssignmentResponse::from).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public DispatchAssignmentResponse getMyAssignment(Long dispatchId, User driver) {
        DispatchAssignment d = dispatchRepo.findByIdAndDriverUserId(dispatchId, driver.getId())
                .orElseThrow(() -> DispatchException.notFound(dispatchId));
        return DispatchAssignmentResponse.from(d);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public DispatchAssignmentResponse acceptAssignment(Long dispatchId, User driver) {
        log.info("acceptAssignment: dispatchId={} driver={}", dispatchId, driver.getId());

        // Load with pessimistic lock to prevent concurrent accept+cancel
        Long freightId = dispatchRepo.findFreightIdById(dispatchId)
                .orElseThrow(() -> DispatchException.notFound(dispatchId));
        Freight freight = freightRepo.findByIdForUpdate(freightId)
                .orElseThrow(() -> DispatchException.freightNotDispatchable(
                        freightId, "freight not found"));
        if (freight.getStatus() == Freight.FreightStatus.CANCELLED) {
            throw DispatchException.freightNotDispatchable(freight.getId(), "CANCELLED");
        }

        DispatchAssignment dispatch;
        try {
            dispatch = dispatchRepo.findByIdForUpdate(dispatchId)
                    .orElseThrow(() -> DispatchException.notFound(dispatchId));
        } catch (ObjectOptimisticLockingFailureException ex) {
            throw DispatchException.concurrentConflict();
        }

        // IDOR: only the assigned driver can accept
        if (!dispatch.getDriverUserId().equals(driver.getId())) {
            throw DispatchException.wrongDriver();
        }

        // Status must be ASSIGNED
        if (dispatch.getStatus() != DispatchStatus.ASSIGNED) {
            throw DispatchException.invalidTransition(dispatch.getStatus(), "accept");
        }

        // Idempotency: if a booking already exists for this dispatch, return current state
        Optional<Booking> existingBooking = bookingRepo.findByDispatchAssignmentId(dispatchId);
        if (existingBooking.isPresent()) {
            dispatch.setStatus(DispatchStatus.DRIVER_ACCEPTED);
            dispatch.setBookingId(existingBooking.get().getId());
            updateAwardAssignmentStatus(dispatch.getCarrierOfferId(),
                    CarrierAssignmentStatus.BOOKING_CREATED);
            log.info("acceptAssignment: idempotent — booking {} already exists", existingBooking.get().getId());
            return DispatchAssignmentResponse.from(dispatch);
        }

        if (freight.getStatus() != Freight.FreightStatus.POSTED) {
            throw DispatchException.freightNotDispatchable(freight.getId(),
                    "freight must remain POSTED before dispatch acceptance");
        }

        // Reload freight + offer to validate still valid
        if (!dispatch.getFreightId().equals(freight.getId())) {
            throw DispatchException.freightNotDispatchable(dispatch.getFreightId(),
                    "dispatch freight relationship changed");
        }
        validateFreightDispatchable(freight);

        CarrierOffer award = offerRepo.findById(dispatch.getCarrierOfferId())
                .orElseThrow(() -> DispatchException.noAward(dispatch.getFreightId()));
        if (award.getStatus() != OfferStatus.ACCEPTED) {
            throw DispatchException.noAward(dispatch.getFreightId());
        }

        // Load driver user and vehicle for booking creation
        CarrierDriver carrierDriver = driverRepo.findByCarrierCompanyIdAndDriverUserIdForUpdate(
                        dispatch.getCarrierCompanyId(), dispatch.getDriverUserId())
                .orElseThrow(() -> DispatchException.crossCompanyDriver());
        User driverUser = userRepo.findById(dispatch.getDriverUserId())
                .orElseThrow(() -> DispatchException.driverIneligible("driver user not found"));
        Vehicle vehicle = vehicleRepo.findByIdForUpdate(dispatch.getVehicleId())
                .orElseThrow(() -> DispatchException.vehicleIneligible("vehicle not found"));

        // Calculate commission (carrier bookings use standard rate — no fleet discount)
        BigDecimal commission = commissionService.calculateCommission(dispatch.getAgreedAmount(), false);

        // Create the booking atomically
        Booking booking;
        try {
            booking = Booking.builder()
                    .cargoRequest(freight)
                    .driver(driverUser)
                    .vehicle(vehicle)
                    .bid(null)                                        // carrier dispatch — no bid
                    .bookingStatus(Booking.BookingStatus.ACCEPTED)
                    .estimatedPrice(dispatch.getAgreedAmount())       // immutable from Phase 5
                    .commissionFee(commission)
                    .carrierOfferId(dispatch.getCarrierOfferId())
                    .dispatchAssignmentId(dispatch.getId())
                    .carrierCompanyId(dispatch.getCarrierCompanyId())
                    .build();
            booking = bookingRepo.saveAndFlush(booking);
        } catch (DataIntegrityViolationException ex) {
            // Unique constraint on dispatch_assignment_id fired — idempotent second call
            Optional<Booking> raceBooking = bookingRepo.findByDispatchAssignmentId(dispatchId);
            if (raceBooking.isPresent()) {
                dispatch.setStatus(DispatchStatus.DRIVER_ACCEPTED);
                dispatch.setBookingId(raceBooking.get().getId());
                dispatch.setRespondedAt(LocalDateTime.now());
                dispatchRepo.save(dispatch);
                updateAwardAssignmentStatus(dispatch.getCarrierOfferId(),
                        CarrierAssignmentStatus.BOOKING_CREATED);
                return DispatchAssignmentResponse.from(dispatch);
            }
            throw DispatchException.concurrentConflict();
        }

        // Update freight status to MATCHED (driver has accepted — shipment is now operational)
        freight.setStatus(Freight.FreightStatus.MATCHED);
        freight.setMatchedDriver(driverUser);
        freightRepo.save(freight);

        // Transition dispatch to DRIVER_ACCEPTED
        dispatch.setStatus(DispatchStatus.DRIVER_ACCEPTED);
        dispatch.setRespondedAt(LocalDateTime.now());
        dispatch.setBookingId(booking.getId());
        dispatchRepo.save(dispatch);
        updateAwardAssignmentStatus(dispatch.getCarrierOfferId(),
                CarrierAssignmentStatus.BOOKING_CREATED);

        // Driver availability → ON_TRIP (actively committed)
        carrierDriver.setAvailability(DriverAvailability.ON_TRIP);
        driverRepo.save(carrierDriver);

        vehicle.setAvailabilityStatus("on_trip");
        vehicleRepo.save(vehicle);

        // Notify the carrier dispatcher
        notifyCarrierOfDriverResponse(dispatch, freight, true);

        log.info("acceptAssignment: dispatchId={} booking={} driver={} amount={}",
                dispatchId, booking.getId(), driver.getId(), dispatch.getAgreedAmount());

        return DispatchAssignmentResponse.from(dispatch);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public DispatchAssignmentResponse rejectAssignment(Long dispatchId, DriverRespondRequest request, User driver) {
        log.info("rejectAssignment: dispatchId={} driver={}", dispatchId, driver.getId());

        Long freightId = dispatchRepo.findFreightIdById(dispatchId)
                .orElseThrow(() -> DispatchException.notFound(dispatchId));
        freightRepo.findByIdForUpdate(freightId)
                .orElseThrow(() -> DispatchException.freightNotDispatchable(
                        freightId, "freight not found"));

        DispatchAssignment dispatch;
        try {
            dispatch = dispatchRepo.findByIdForUpdate(dispatchId)
                    .orElseThrow(() -> DispatchException.notFound(dispatchId));
        } catch (ObjectOptimisticLockingFailureException ex) {
            throw DispatchException.concurrentConflict();
        }

        // IDOR: only the assigned driver can reject
        if (!dispatch.getDriverUserId().equals(driver.getId())) {
            throw DispatchException.wrongDriver();
        }

        if (dispatch.getStatus() != DispatchStatus.ASSIGNED) {
            throw DispatchException.invalidTransition(dispatch.getStatus(), "reject");
        }

        dispatch.setStatus(DispatchStatus.DRIVER_REJECTED);
        dispatch.setRespondedAt(LocalDateTime.now());
        if (request != null && request.getRejectionReason() != null) {
            String reason = request.getRejectionReason().strip();
            dispatch.setRejectionReason(reason.isEmpty() ? null : reason);
        }
        dispatch = dispatchRepo.save(dispatch);
        updateAwardAssignmentStatus(dispatch.getCarrierOfferId(),
                CarrierAssignmentStatus.PENDING_ASSIGNMENT);

        // Release driver and vehicle — carrier can redispatch
        releaseDriverAndVehicle(dispatch);

        // Freight remains in POSTED status — commercial award is NOT revoked
        // The winning company still owns the job; they may dispatch another driver

        // Notify carrier
        Freight freight = freightRepo.findById(dispatch.getFreightId()).orElse(null);
        notifyCarrierOfDriverResponse(dispatch, freight, false);

        log.info("rejectAssignment: dispatchId={} rejected by driver={} reason={}",
                dispatchId, driver.getId(), dispatch.getRejectionReason());

        return DispatchAssignmentResponse.from(dispatch);
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Internal helpers
    // ════════════════════════════════════════════════════════════════════════════

    /** Require caller to have a dispatch-eligible role (OWNER, FLEET_MANAGER, DISPATCHER). */
    private void requireDispatchRole(CompanyContext ctx) {
        CompanyRole role = ctx.membership().getRole();
        if (role != CompanyRole.CARRIER_OWNER
                && role != CompanyRole.FLEET_MANAGER
                && role != CompanyRole.DISPATCHER) {
            throw new com.ethioloadai.carrier.team.exception.CompanyTeamException(
                    "Dispatch requires CARRIER_OWNER, FLEET_MANAGER, or DISPATCHER role",
                    "DISPATCH_ROLE_REQUIRED", 403);
        }
    }

    private void updateAwardAssignmentStatus(Long offerId, CarrierAssignmentStatus status) {
        CarrierOffer award = offerRepo.findById(offerId)
                .filter(offer -> offer.getStatus() == OfferStatus.ACCEPTED)
                .orElseThrow(() -> DispatchException.noAward(offerId));
        award.setAssignmentStatus(status);
        offerRepo.save(award);
    }

    /** Validate the freight is in a state where dispatch is allowed. */
    private void validateFreightDispatchable(Freight freight) {
        switch (freight.getStatus()) {
            case COMPLETED -> throw DispatchException.freightNotDispatchable(freight.getId(), "already COMPLETED");
            case CANCELLED  -> throw DispatchException.freightNotDispatchable(freight.getId(), "CANCELLED");
            case IN_TRANSIT -> throw DispatchException.freightNotDispatchable(freight.getId(), "already IN_TRANSIT");
            case DELIVERED  -> throw DispatchException.freightNotDispatchable(freight.getId(), "already DELIVERED");
            default -> { /* POSTED or MATCHED — allow */ }
        }
    }

    /**
     * Validate driver belongs to the winning company, is ACTIVE, and AVAILABLE.
     * Returns the CarrierDriver record for further manipulation.
     */
    private CarrierDriver validateDriver(Long driverUserId, Long companyId) {
        // Cross-company check — must belong to winning company
        CarrierDriver cd = driverRepo.findByCarrierCompanyIdAndDriverUserIdForUpdate(companyId, driverUserId)
                .orElseThrow(DispatchException::crossCompanyDriver);

        if (cd.getStatus() != CarrierDriverStatus.ACTIVE) {
            throw DispatchException.driverIneligible(
                    "driver CarrierDriver status is " + cd.getStatus() + " (must be ACTIVE)");
        }
        if (cd.getAvailability() == DriverAvailability.UNAVAILABLE) {
            throw DispatchException.driverIneligible("driver is marked UNAVAILABLE");
        }
        if (cd.getAvailability() == DriverAvailability.ON_TRIP) {
            throw DispatchException.driverIneligible("driver is currently ON_TRIP");
        }
        // ASSIGNED is acceptable here — the unique partial index will prevent double-dispatch;
        // we check the pending dispatch separately above.

        // Verify driver User account is valid
        userRepo.findById(driverUserId)
                .filter(u -> Boolean.TRUE.equals(u.getIsActive()))
                .filter(u -> u.getRole() == User.Role.DRIVER)
                .orElseThrow(() -> DispatchException.driverIneligible("driver user account is inactive or not a DRIVER"));

        return cd;
    }

    /**
     * Validate vehicle belongs to the winning company, is APPROVED, and AVAILABLE.
     * Also validates capacity against freight weight if both are present.
     */
    private Vehicle validateVehicle(Long vehicleId, Long companyId, Freight freight) {
        // Cross-company check
        Vehicle vehicle = vehicleRepo.findByIdAndCarrierCompanyIdForUpdate(vehicleId, companyId)
                .orElseThrow(DispatchException::crossCompanyVehicle);

        if (vehicle.getApprovalStatus() != Vehicle.VehicleApprovalStatus.APPROVED) {
            throw DispatchException.vehicleIneligible(
                    "vehicle is not APPROVED (current: " + vehicle.getApprovalStatus() + ")");
        }

        String avail = vehicle.getAvailabilityStatus();
        if ("on_trip".equalsIgnoreCase(avail)) {
            throw DispatchException.vehicleIneligible("vehicle is currently ON_TRIP");
        }
        if ("maintenance".equalsIgnoreCase(avail)) {
            throw DispatchException.vehicleIneligible("vehicle is under MAINTENANCE");
        }
        if ("unavailable".equalsIgnoreCase(avail)) {
            throw DispatchException.vehicleIneligible("vehicle is marked UNAVAILABLE");
        }

        // Capacity validation: vehicle.capacity is in tons; freight.weightTons in tons
        if (vehicle.getCapacity() != null && freight.getWeightTons() != null) {
            double vehicleTons = vehicle.getCapacity(); // stored as tons
            double freightTons = freight.getWeightTons().doubleValue();
            if (vehicleTons < freightTons) {
                throw DispatchException.insufficientCapacity(vehicleTons, freightTons);
            }
        }

        return vehicle;
    }

    /** Release driver and vehicle reservations back to AVAILABLE. */
    private void releaseDriverAndVehicle(DispatchAssignment dispatch) {
        // Release driver availability
        driverRepo.findByCarrierCompanyIdAndDriverUserIdForUpdate(
                dispatch.getCarrierCompanyId(), dispatch.getDriverUserId())
                .ifPresent(cd -> {
                    if (cd.getStatus() == CarrierDriverStatus.ACTIVE
                            && cd.getAvailability() == DriverAvailability.ASSIGNED) {
                        cd.setAvailability(DriverAvailability.AVAILABLE);
                        driverRepo.save(cd);
                        log.debug("Released driver {} back to AVAILABLE", dispatch.getDriverUserId());
                    }
                });

        // Release vehicle availability
        vehicleRepo.findByIdForUpdate(dispatch.getVehicleId()).ifPresent(v -> {
            if ("assigned".equalsIgnoreCase(v.getAvailabilityStatus())) {
                v.setAvailabilityStatus("available");
                vehicleRepo.save(v);
                log.debug("Released vehicle {} back to available", dispatch.getVehicleId());
            }
        });
    }

    /** Send assignment notification to the selected driver. */
    private void notifyDriver(Long driverUserId, Freight freight, DispatchAssignment dispatch,
                              CarrierCompany company) {
        try {
            notificationService.recordNotification(driverUserId, "DISPATCH_ASSIGNMENT",
                    Map.of(
                            "dispatchId", dispatch.getId(),
                            "freightId", freight.getId(),
                            "origin", freight.getPickupLocation(),
                            "destination", freight.getDestination(),
                            "materialType", freight.getMaterialType(),
                            "vehicleId", dispatch.getVehicleId(),
                            "carrierCompany", company.getCompanyName(),
                            "agreedAmount", dispatch.getAgreedAmount(),
                            "assignedAt", dispatch.getAssignedAt().toString(),
                            "message", "You have a new shipment assignment. Please review and respond."
                    ));
        } catch (Exception ex) {
            log.warn("Failed to notify driver {} for dispatch {}: {}",
                    driverUserId, dispatch.getId(), ex.getMessage());
            // Non-fatal — dispatch state is already persisted
        }
    }

    /** Notify the dispatcher/carrier owner when driver responds. */
    private void notifyCarrierOfDriverResponse(DispatchAssignment dispatch, Freight freight,
                                                boolean accepted) {
        try {
            // Notify the original assigner
            String type = accepted ? "DISPATCH_DRIVER_ACCEPTED" : "DISPATCH_DRIVER_REJECTED";
            notificationService.recordNotification(dispatch.getAssignedBy(), type,
                    Map.of(
                            "dispatchId", dispatch.getId(),
                            "freightId", dispatch.getFreightId(),
                            "driverUserId", dispatch.getDriverUserId(),
                            "accepted", accepted,
                            "rejectionReason", dispatch.getRejectionReason() != null
                                    ? dispatch.getRejectionReason() : ""
                    ));
        } catch (Exception ex) {
            log.warn("Failed to notify carrier {} for dispatch {} response: {}",
                    dispatch.getAssignedBy(), dispatch.getId(), ex.getMessage());
        }
    }
}
