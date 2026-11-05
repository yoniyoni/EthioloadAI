package com.ethioloadai.contract.service;

import com.ethioloadai.booking.entity.Booking;
import com.ethioloadai.booking.repository.BookingRepository;
import com.ethioloadai.contract.dto.ContractFreightDto;
import com.ethioloadai.contract.dto.ContractPartyDto;
import com.ethioloadai.contract.dto.ContractResponse;
import com.ethioloadai.contract.exception.ContractException.ContractNotFoundException;
import com.ethioloadai.contract.exception.ContractException.UnauthorizedContractAccessException;
import com.ethioloadai.freight.entity.Freight;
import com.ethioloadai.freight.repository.FreightRepository;
import com.ethioloadai.freight.service.FreightVisibilityProperties;
import com.ethioloadai.payment.entity.Payment;
import com.ethioloadai.payment.repository.PaymentRepository;
import com.ethioloadai.trip.repository.TripRepository;
import com.ethioloadai.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ContractServiceImpl implements ContractService {

    private static final Logger log = LoggerFactory.getLogger(ContractServiceImpl.class);

    private final FreightRepository freightRepository;
    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final TripRepository tripRepository;
    private final FreightVisibilityProperties visibilityProperties;

    @Override
    @Transactional(readOnly = true)
    public ContractResponse getContractByFreightId(Long freightId, User caller) {
        if (freightId == null) {
            throw new ContractNotFoundException("Freight ID must not be null");
        }

        Freight freight = freightRepository.findById(freightId)
                .orElseThrow(() -> new ContractNotFoundException("Freight not found with ID: " + freightId));

        Optional<Booking> activeBooking = bookingRepository
                .findFirstByCargoRequest_IdAndBookingStatusNotOrderByIdDesc(
                        freightId, Booking.BookingStatus.CANCELLED);

        // Verify authorization
        verifyParticipantAuthorization(freight, activeBooking.orElse(null), caller);
        if (caller.getRole() == User.Role.DRIVER
                && activeBooking.isPresent()
                && !isDriverHistoryVisible(freight, activeBooking.get())) {
            throw new UnauthorizedContractAccessException(
                    "Historical freight access window has expired");
        }

        // Ensure there is an agreement/counterparty (either an active booking or matched driver)
        User driverUser = activeBooking.map(Booking::getDriver).orElse(freight.getMatchedDriver());
        if (driverUser == null && activeBooking.isEmpty()) {
            throw new ContractNotFoundException("No contract found for freight #" + freightId +
                    ". Contracts are generated after driver matching or payment confirmation.");
        }

        // Determine contract ID (booking ID, or fallback to freight ID)
        Long contractId = activeBooking.map(Booking::getId).orElse(freight.getId());

        // Determine agreed price
        BigDecimal agreedPrice = activeBooking.map(Booking::getEstimatedPrice)
                .or(() -> Optional.ofNullable(freight.getMatchedPrice()))
                .orElse(freight.getBudget());

        // Determine payment status
        Optional<Payment> payment = activeBooking.flatMap(b -> paymentRepository.findByBooking_Id(b.getId()));
        String paymentStatus = resolvePaymentStatus(payment.orElse(null));

        // Determine contract status
        String contractStatus = resolveContractStatus(freight, activeBooking.orElse(null), payment.orElse(null));

        ContractPartyDto shipperDto = new ContractPartyDto(
                freight.getShipper() != null ? freight.getShipper().getFullName() : "Unknown",
                freight.getShipper() != null ? freight.getShipper().getEmail() : "No email"
        );

        ContractPartyDto driverDto = activeBooking.isPresent()
                ? new ContractPartyDto(
                        driverUser != null ? driverUser.getFullName() : "Unknown",
                        driverUser != null ? driverUser.getEmail() : "No email")
                : new ContractPartyDto("Assigned driver", "Contact details unavailable");

        ContractFreightDto freightDto = new ContractFreightDto(
                freight.getPickupLocation() != null ? freight.getPickupLocation() : "-",
                freight.getDestination() != null ? freight.getDestination() : "-"
        );

        return new ContractResponse(
                contractId,
                freight.getId(),
                contractStatus,
                paymentStatus,
                agreedPrice,
                freight.getDeadline(),
                shipperDto,
                driverDto,
                freightDto
        );
    }

    private void verifyParticipantAuthorization(Freight freight, Booking activeBooking, User caller) {
        if (caller == null) {
            throw new UnauthorizedContractAccessException("User must be authenticated to view contracts");
        }

        if (caller.getRole() == User.Role.ADMIN) {
            return;
        }

        boolean isShipper = freight.getShipper() != null && freight.getShipper().getId().equals(caller.getId());
        boolean isBookedDriver = activeBooking != null && activeBooking.getDriver() != null &&
                activeBooking.getDriver().getId().equals(caller.getId());

        if (!isShipper && !isBookedDriver) {
            log.warn("Unauthorized contract access attempt: freightId={} callerId={}", freight.getId(), caller.getId());
            throw new UnauthorizedContractAccessException("You are not authorized to view the contract for this freight");
        }
    }

    private String resolvePaymentStatus(Payment payment) {
        if (payment == null) {
            return "pending";
        }
        return switch (payment.getPaymentStatus()) {
            case PAID -> "paid";
            case FAILED -> "failed";
            default -> "pending";
        };
    }

    private boolean isDriverHistoryVisible(Freight freight, Booking booking) {
        LocalDateTime terminalAt = null;
        if (freight.getStatus() == Freight.FreightStatus.CANCELLED) {
            terminalAt = freight.getUpdatedAt();
        } else {
            terminalAt = tripRepository.findByBooking_Id(booking.getId())
                    .filter(trip -> trip.getTripStatus()
                            == com.ethioloadai.trip.entity.Trip.TripStatus.COMPLETED)
                    .map(com.ethioloadai.trip.entity.Trip::getEndTime)
                    .orElse(null);
            if (terminalAt == null
                    && (booking.getBookingStatus() == Booking.BookingStatus.COMPLETED
                    || booking.getBookingStatus() == Booking.BookingStatus.CANCELLED)) {
                terminalAt = booking.getUpdatedAt();
            }
            if (terminalAt == null && freight.getStatus() == Freight.FreightStatus.COMPLETED) {
                terminalAt = freight.getUpdatedAt();
            }
            if (terminalAt == null) {
                return true;
            }
        }
        LocalDateTime now = LocalDateTime.now();
        long historyDays = visibilityProperties.getDriverHistoryDays();
        return terminalAt != null
                && historyDays > 0
                && !terminalAt.isAfter(now)
                && !terminalAt.isBefore(now.minusDays(historyDays));
    }

    private String resolveContractStatus(Freight freight, Booking booking, Payment payment) {
        if (freight.getStatus() == Freight.FreightStatus.COMPLETED ||
                (booking != null && booking.getBookingStatus() == Booking.BookingStatus.COMPLETED)) {
            return "completed";
        }

        if (freight.getStatus() == Freight.FreightStatus.CANCELLED ||
                (booking != null && booking.getBookingStatus() == Booking.BookingStatus.CANCELLED)) {
            return "cancelled";
        }

        if (freight.getStatus() == Freight.FreightStatus.POSTED && booking == null) {
            return "draft";
        }

        // Active statuses: matched, in_transit, delivered, accepted booking, or paid payment
        return "active";
    }
}
