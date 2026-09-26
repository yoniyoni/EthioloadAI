package com.ethioloadai.settlement.service;

import com.ethioloadai.bid.service.CommissionService;
import com.ethioloadai.booking.entity.Booking;
import com.ethioloadai.booking.repository.BookingRepository;
import com.ethioloadai.carrier.company.entity.CarrierCompany;
import com.ethioloadai.carrier.company.repository.CarrierCompanyRepository;
import com.ethioloadai.carrier.team.service.CompanySecurityService;
import com.ethioloadai.notification.service.NotificationStoreService;
import com.ethioloadai.settlement.dto.CarrierSettlementResponse;
import com.ethioloadai.settlement.entity.CarrierSettlement;
import com.ethioloadai.settlement.entity.CarrierSettlement.SettlementStatus;
import com.ethioloadai.settlement.exception.SettlementException;
import com.ethioloadai.settlement.repository.CarrierSettlementRepository;
import com.ethioloadai.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Phase 7 — Carrier Settlement service implementation.
 *
 * <h2>Security invariants</h2>
 * <ul>
 *   <li>All amounts server-calculated. No client-supplied grossAmount/commission/netAmount.</li>
 *   <li>Settlement recipient = CarrierCompany. Driver is NOT the recipient.</li>
 *   <li>Commission rate read from {@link CommissionService} (10% standard).</li>
 *   <li>Carrier dispatch bookings always use standard rate (no fleet discount).</li>
 * </ul>
 *
 * <h2>Idempotency</h2>
 * <ul>
 *   <li>DB unique constraint on booking_id prevents duplicate rows.</li>
 *   <li>{@code createSettlement} checks for existing record first.</li>
 *   <li>Concurrent calls are handled via pessimistic lock + DataIntegrityViolationException catch.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class CarrierSettlementServiceImpl implements CarrierSettlementService {

    private static final Logger log = LoggerFactory.getLogger(CarrierSettlementServiceImpl.class);

    private final CarrierSettlementRepository settlementRepo;
    private final BookingRepository bookingRepo;
    private final CarrierCompanyRepository carrierCompanyRepo;
    private final CommissionService commissionService;
    private final CompanySecurityService companySecurityService;
    private final NotificationStoreService notificationService;

    // ── Create settlement ─────────────────────────────────────────────────────

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CarrierSettlementResponse createSettlement(Long bookingId, Long proofOfDeliveryId) {
        log.info("createSettlement: bookingId={} proofId={}", bookingId, proofOfDeliveryId);

        // 1. Idempotency: return existing if already created
        Optional<CarrierSettlement> existing = settlementRepo.findByBooking_Id(bookingId);
        if (existing.isPresent()) {
            log.info("createSettlement: settlement already exists for booking {} (idempotent)", bookingId);
            return CarrierSettlementResponse.from(existing.get());
        }

        // 2. Load booking
        Booking booking = bookingRepo.findById(bookingId)
                .orElseThrow(() -> new SettlementException.NotFoundException("Booking not found: " + bookingId));

        // 3. Validate booking is a carrier dispatch booking
        if (booking.getCarrierCompanyId() == null) {
            throw new SettlementException.InvalidStateException(
                    "Booking " + bookingId + " is not a carrier dispatch booking — no settlement applicable");
        }

        // 4. Load carrier company
        CarrierCompany carrier = carrierCompanyRepo.findById(booking.getCarrierCompanyId())
                .orElseThrow(() -> new SettlementException.NotFoundException(
                        "Carrier company not found: " + booking.getCarrierCompanyId()));

        // 5. Calculate amounts — all server-side, BigDecimal with HALF_UP
        BigDecimal gross = booking.getEstimatedPrice();
        if (gross == null || gross.compareTo(BigDecimal.ZERO) <= 0) {
            throw new SettlementException.InvalidStateException(
                    "Booking " + bookingId + " has no valid estimated price: " + gross);
        }
        gross = gross.setScale(2, RoundingMode.HALF_UP);

        // Carrier dispatch bookings always use STANDARD commission (10%)
        // They never qualify for fleet discount (5%)
        BigDecimal commissionRate = commissionService.getCommissionRate(false);  // false = standard
        BigDecimal commissionAmount = gross.multiply(commissionRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal netAmount = gross.subtract(commissionAmount).setScale(2, RoundingMode.HALF_UP);

        log.info("createSettlement: booking={} gross={} rate={} commission={} net={}",
                bookingId, gross, commissionRate, commissionAmount, netAmount);

        // 6. Build and save settlement
        String idempotencyKey = "settlement-booking-" + bookingId;
        CarrierSettlement settlement = CarrierSettlement.builder()
                .booking(booking)
                .carrierCompany(carrier)
                .proofOfDeliveryId(proofOfDeliveryId)
                .grossAmount(gross)
                .commissionRate(commissionRate)
                .commissionAmount(commissionAmount)
                .netAmount(netAmount)
                .currency("ETB")
                .status(SettlementStatus.PENDING)
                .idempotencyKey(idempotencyKey)
                .build();

        try {
            settlement = settlementRepo.save(settlement);
        } catch (DataIntegrityViolationException ex) {
            log.warn("createSettlement: concurrent duplicate for booking {} — returning existing", bookingId);
            return CarrierSettlementResponse.from(
                    settlementRepo.findByBooking_Id(bookingId)
                            .orElseThrow(() -> new SettlementException.DuplicateSettlementException(bookingId)));
        }

        // 7. Notify carrier owner
        notifyCarrier(carrier, booking, settlement);

        log.info("createSettlement: settlement {} created for carrier {} booking {}",
                settlement.getId(), carrier.getId(), bookingId);
        return CarrierSettlementResponse.from(settlement);
    }

    // ── Query ─────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public CarrierSettlementResponse getSettlement(Long settlementId, User caller) {
        CarrierSettlement s = settlementRepo.findById(settlementId)
                .orElseThrow(() -> SettlementException.notFound(settlementId));
        authorizeRead(s, caller);
        return CarrierSettlementResponse.from(s);
    }

    @Override
    @Transactional(readOnly = true)
    public CarrierSettlementResponse getSettlementByBookingId(Long bookingId, User caller) {
        CarrierSettlement s = settlementRepo.findByBooking_Id(bookingId)
                .orElseThrow(() -> new SettlementException.NotFoundException(
                        "No settlement found for booking " + bookingId));
        authorizeRead(s, caller);
        return CarrierSettlementResponse.from(s);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CarrierSettlementResponse> getCarrierSettlements(Long carrierCompanyId, User caller) {
        // Must be active carrier member or admin
        if (caller.getRole() != User.Role.ADMIN) {
            companySecurityService.requireMember(carrierCompanyId, caller);
        }
        return settlementRepo.findByCarrierCompanyId(carrierCompanyId).stream()
                .map(CarrierSettlementResponse::from)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<CarrierSettlementResponse> getAllSettlements(User admin) {
        requireAdmin(admin);
        return settlementRepo.findAllOrderByCreatedAtDesc().stream()
                .map(CarrierSettlementResponse::from)
                .collect(Collectors.toList());
    }

    // ── Admin lifecycle transitions ────────────────────────────────────────────

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CarrierSettlementResponse markProcessing(Long settlementId, User admin) {
        requireAdmin(admin);
        CarrierSettlement s = lockAndLoad(settlementId);
        assertStatus(s, SettlementStatus.PENDING, "mark as PROCESSING");
        s.setStatus(SettlementStatus.PROCESSING);
        s.setProcessedAt(LocalDateTime.now());
        return CarrierSettlementResponse.from(settlementRepo.save(s));
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CarrierSettlementResponse markPaid(Long settlementId, String externalTxId,
                                              String paymentReference, User admin) {
        requireAdmin(admin);
        CarrierSettlement s = lockAndLoad(settlementId);
        if (s.getStatus() != SettlementStatus.PROCESSING && s.getStatus() != SettlementStatus.PENDING) {
            throw SettlementException.invalidTransition(s.getStatus(), "mark as PAID");
        }
        // Idempotency: already paid
        if (s.getStatus() == SettlementStatus.PAID) return CarrierSettlementResponse.from(s);

        s.setStatus(SettlementStatus.PAID);
        s.setPaidAt(LocalDateTime.now());
        s.setExternalTxId(externalTxId);
        s.setPaymentReference(paymentReference);

        CarrierSettlement saved = settlementRepo.save(s);

        // Notify carrier
        try {
            notificationService.recordNotification(
                    s.getCarrierCompany().getOwnerId(),
                    "SETTLEMENT_PAID",
                    Map.of("settlementId", s.getId(),
                           "bookingId", s.getBooking().getId(),
                           "netAmount", s.getNetAmount(),
                           "message", "Your settlement payment has been processed."));
        } catch (Exception e) { log.warn("Settlement paid notification failed: {}", e.getMessage()); }

        log.info("markPaid: settlement {} PAID for carrier {}", settlementId, s.getCarrierCompany().getId());
        return CarrierSettlementResponse.from(saved);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CarrierSettlementResponse markFailed(Long settlementId, String reason, User admin) {
        requireAdmin(admin);
        CarrierSettlement s = lockAndLoad(settlementId);
        if (s.getStatus().isTerminal()) {
            throw SettlementException.invalidTransition(s.getStatus(), "mark as FAILED");
        }
        s.setStatus(SettlementStatus.FAILED);
        s.setFailedAt(LocalDateTime.now());
        s.setFailureReason(reason);
        log.warn("markFailed: settlement {} FAILED — reason: {}", settlementId, reason);
        return CarrierSettlementResponse.from(settlementRepo.save(s));
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CarrierSettlementResponse retrySettlement(Long settlementId, User admin) {
        requireAdmin(admin);
        CarrierSettlement s = lockAndLoad(settlementId);
        if (!s.getStatus().isRetryable()) {
            throw SettlementException.invalidTransition(s.getStatus(), "retry");
        }
        // Must reuse SAME amounts — do NOT recalculate
        s.setStatus(SettlementStatus.PENDING);
        s.setFailedAt(null);
        s.setFailureReason(null);
        s.setRetryCount(s.getRetryCount() + 1);
        log.info("retrySettlement: settlement {} retry #{}", settlementId, s.getRetryCount());
        return CarrierSettlementResponse.from(settlementRepo.save(s));
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CarrierSettlementResponse blockSettlement(Long settlementId, String reason, User admin) {
        requireAdmin(admin);
        CarrierSettlement s = lockAndLoad(settlementId);
        if (s.getStatus().isTerminal()) {
            throw SettlementException.invalidTransition(s.getStatus(), "block");
        }
        s.setStatus(SettlementStatus.BLOCKED);
        s.setFailureReason(reason);
        log.info("blockSettlement: settlement {} BLOCKED — reason: {}", settlementId, reason);
        return CarrierSettlementResponse.from(settlementRepo.save(s));
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CarrierSettlementResponse unblockSettlement(Long settlementId, User admin) {
        requireAdmin(admin);
        CarrierSettlement s = lockAndLoad(settlementId);
        assertStatus(s, SettlementStatus.BLOCKED, "unblock");
        s.setStatus(SettlementStatus.PENDING);
        s.setFailureReason(null);
        log.info("unblockSettlement: settlement {} → PENDING", settlementId);
        return CarrierSettlementResponse.from(settlementRepo.save(s));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private CarrierSettlement lockAndLoad(Long settlementId) {
        return settlementRepo.findById(settlementId)
                .orElseThrow(() -> SettlementException.notFound(settlementId));
    }

    private void assertStatus(CarrierSettlement s, SettlementStatus expected, String action) {
        if (s.getStatus() != expected) {
            throw SettlementException.invalidTransition(s.getStatus(), action);
        }
    }

    private void requireAdmin(User user) {
        if (user.getRole() != User.Role.ADMIN) {
            throw new SettlementException.AuthorizationException("Only administrators may perform this action");
        }
    }

    private void authorizeRead(CarrierSettlement s, User caller) {
        if (caller.getRole() == User.Role.ADMIN) return;
        Long carrierId = s.getCarrierCompany().getId();
        // Carrier member can see their own settlements
        if (companySecurityService.isActiveMemberOf(caller.getId(), carrierId)) return;
        // Shipper can see settlement for their own freight
        if (s.getBooking().getCargoRequest().getShipper().getId().equals(caller.getId())) return;
        throw SettlementException.unauthorized("you are not authorized to view this settlement");
    }

    private void notifyCarrier(CarrierCompany carrier, Booking booking, CarrierSettlement settlement) {
        try {
            notificationService.recordNotification(
                    carrier.getOwnerId(),
                    "SETTLEMENT_CREATED",
                    Map.of(
                            "settlementId", settlement.getId(),
                            "bookingId", booking.getId(),
                            "grossAmount", settlement.getGrossAmount(),
                            "commissionAmount", settlement.getCommissionAmount(),
                            "netAmount", settlement.getNetAmount(),
                            "message", "A settlement has been created for your completed delivery."
                    ));
        } catch (Exception e) {
            log.warn("Settlement notification failed (non-fatal): {}", e.getMessage());
        }
    }
}
