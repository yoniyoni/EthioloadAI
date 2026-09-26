package com.ethioloadai.booking.service;

import com.ethioloadai.bid.entity.Bid;
import com.ethioloadai.bid.exception.BidNotFoundException;
import com.ethioloadai.bid.exception.BidStatusException;
import com.ethioloadai.bid.repository.BidRepository;
import com.ethioloadai.bid.service.CommissionService;
import com.ethioloadai.booking.entity.Booking;
import com.ethioloadai.booking.exception.BookingNotFoundException;
import com.ethioloadai.booking.repository.BookingRepository;
import com.ethioloadai.exception.AuthorizationException;
import com.ethioloadai.freight.entity.Freight;
import com.ethioloadai.freight.repository.FreightRepository;
import com.ethioloadai.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.ethioloadai.booking.dto.AdminUnpaidBookingResponse;
import com.ethioloadai.booking.dto.BookingResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.hibernate.Hibernate;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {
    private static final Logger log = LoggerFactory.getLogger(BookingServiceImpl.class);

    private final BidRepository bidRepository;
    private final BookingRepository bookingRepository;
    private final FreightRepository freightRepository;
    private final CommissionService commissionService;
    private final EntityManager entityManager;

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Booking acceptBid(Long bidId, User shipper) {
        log.info("Accepting bid {} by shipper {}", bidId, shipper.getId());

        // CRITICAL: Use pessimistic locking to prevent concurrent bid acceptances
        // This fixes the Laravel race condition vulnerability
        Bid bid = entityManager.find(Bid.class, bidId, LockModeType.PESSIMISTIC_WRITE);
        if (bid == null) {
            throw new BidNotFoundException(bidId);
        }

        // Validate authorization
        if (!bid.getCargoRequest().getShipper().getId().equals(shipper.getId()) && 
            shipper.getRole() != User.Role.ADMIN) {
            throw new AuthorizationException("You can only accept bids for your own freight");
        }

        // Validate bid status INSIDE the transaction (critical for concurrency safety)
        if (bid.getStatus() != Bid.BidStatus.PENDING) {
            throw BidStatusException.notEligibleFor("accept", bid.getStatus());
        }

        // Lock the freight to prevent concurrent modifications
        Freight freight = entityManager.find(Freight.class, bid.getCargoRequest().getId(), LockModeType.PESSIMISTIC_WRITE);
        
        // Validate freight status
        if (freight.getStatus() != Freight.FreightStatus.POSTED) {
            throw new BidStatusException("Freight is no longer available for booking (status: " + freight.getStatus() + ")");
        }

        // Check for existing bookings (double-booking protection)
        boolean hasActiveBooking = bookingRepository.existsByCargoRequest_IdAndBookingStatusIn(
                freight.getId(), 
                Arrays.asList(Booking.BookingStatus.PENDING, Booking.BookingStatus.ACCEPTED)
        );
        if (hasActiveBooking) {
            throw new BidStatusException("This freight already has an active booking");
        }

        // All validations passed - perform atomic operations
        boolean isFleetBooking = bid.getDriver().getFleetOwnerId() != null;
        return performAcceptance(bid, freight, bid.getAmount(), isFleetBooking);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Booking acceptCounterOffer(Long bidId, User actor) {
        log.info("Accepting counter-offer on bid {} by user {}", bidId, actor.getId());

        // CRITICAL: Use pessimistic locking
        Bid bid = entityManager.find(Bid.class, bidId, LockModeType.PESSIMISTIC_WRITE);
        if (bid == null) {
            throw new BidNotFoundException(bidId);
        }

        // Validate counter-offer status
        if (bid.getStatus() != Bid.BidStatus.COUNTERED || bid.getCounterAmount() == null) {
            throw new BidStatusException("No active counter-offer to accept");
        }

        // Validate authorization and determine who's accepting
        boolean isShipper = bid.getCargoRequest().getShipper().getId().equals(actor.getId());
        boolean isDriver = bid.getDriver().getId().equals(actor.getId());

        if (!isShipper && !isDriver && actor.getRole() != User.Role.ADMIN) {
            throw new AuthorizationException("You are not authorized to accept this counter-offer");
        }

        // Validate counter-offer direction
        if (isShipper && bid.getCounterBy() != Bid.CounterBy.DRIVER) {
            throw new BidStatusException("No driver counter-offer to accept");
        }
        if (isDriver && bid.getCounterBy() != Bid.CounterBy.SHIPPER) {
            throw new BidStatusException("No shipper counter-offer to accept");
        }

        // Lock the freight
        Freight freight = entityManager.find(Freight.class, bid.getCargoRequest().getId(), LockModeType.PESSIMISTIC_WRITE);
        
        // Validate freight status
        if (freight.getStatus() != Freight.FreightStatus.POSTED) {
            throw new BidStatusException("Freight is no longer available for booking (status: " + freight.getStatus() + ")");
        }

        // Check for existing bookings
        boolean hasActiveBooking = bookingRepository.existsByCargoRequest_IdAndBookingStatusIn(
                freight.getId(), 
                Arrays.asList(Booking.BookingStatus.PENDING, Booking.BookingStatus.ACCEPTED)
        );
        if (hasActiveBooking) {
            throw new BidStatusException("This freight already has an active booking");
        }

        // Determine if this is a fleet booking for commission calculation
        boolean isFleetBooking = bid.getDriver().getRole() == User.Role.DRIVER && 
                                bid.getDriver().getFleetOwnerId() != null;

        // All validations passed - perform atomic operations with counter amount
        return performAcceptance(bid, freight, bid.getCounterAmount(), isFleetBooking);
    }

    /**
     * Atomic bid acceptance operation - performed within the same transaction.
     * This method handles:
     * 1. Updating bid status to ACCEPTED
     * 2. Creating the booking
     * 3. Rejecting competing bids
     * 4. Updating freight status
     */
    private Booking performAcceptance(Bid acceptedBid, Freight freight, BigDecimal finalAmount, boolean isFleetBooking) {
        log.info("Performing atomic acceptance for bid {} with amount {}", acceptedBid.getId(), finalAmount);

        // 1. Update the accepted bid status and final amount
        acceptedBid.setStatus(Bid.BidStatus.ACCEPTED);
        acceptedBid.setAmount(finalAmount); // Use counter amount if it was a counter-offer acceptance
        bidRepository.save(acceptedBid);

        // 2. Reject all competing bids atomically
        int rejectedCount = bidRepository.rejectCompetingBids(freight.getId(), acceptedBid.getId());
        log.info("Rejected {} competing bids for freight {}", rejectedCount, freight.getId());

        // 3. Update freight status to matched
        freight.setStatus(Freight.FreightStatus.MATCHED);
        freightRepository.save(freight);

        // 4. Calculate commission
        BigDecimal commission = commissionService.calculateCommission(finalAmount, isFleetBooking);

        // 5. Create booking
        Booking booking = Booking.builder()
                .cargoRequest(freight)
                .vehicle(acceptedBid.getVehicle())
                .driver(acceptedBid.getDriver())
                .bid(acceptedBid)
                .bookingStatus(Booking.BookingStatus.ACCEPTED)
                .estimatedPrice(finalAmount)
                .commissionFee(commission)
                .build();

        booking = bookingRepository.save(booking);

        Hibernate.initialize(booking.getCargoRequest());
        Hibernate.initialize(booking.getVehicle());
        Hibernate.initialize(booking.getDriver());

        log.info("Booking created successfully - ID: {}, freight: {}, driver: {}, amount: {}, commission: {}", 
                booking.getId(), freight.getId(), acceptedBid.getDriver().getId(), finalAmount, commission);

        return booking;
    }

    @Override
    @Transactional(readOnly = true)
    public Booking getBookingWithAuthorization(Long bookingId, User user) {
        log.info("Getting booking {} for user {}", bookingId, user.getId());

        Booking booking = bookingRepository.findByIdWithAssociations(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));

        // Check authorization: booking driver, freight shipper, or admin
        boolean isDriver = booking.getDriver().getId().equals(user.getId());
        boolean isShipper = booking.getCargoRequest().getShipper().getId().equals(user.getId());
        boolean isAdmin = user.getRole() == User.Role.ADMIN;

        if (!isDriver && !isShipper && !isAdmin) {
            throw new AuthorizationException("You are not authorized to view this booking");
        }

        return booking;
    }

    @Override
    @Transactional(readOnly = true)
    public BookingResponse getBookingResponseWithAuthorization(Long bookingId, User user) {
        log.info("Getting booking response {} for user {}", bookingId, user.getId());
        
        Booking booking = getBookingWithAuthorization(bookingId, user);
        return mapToBookingResponse(booking);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookingResponse> getBookingsForCaller(User user) {
        List<Booking> bookings = switch (user.getRole()) {
            case DRIVER -> bookingRepository.findByDriver_Id(user.getId());
            case SHIPPER -> bookingRepository.findByShipperId(user.getId());
            case ADMIN -> bookingRepository.findAll();
            case FLEET_OWNER -> bookingRepository.findByDriver_Id(user.getId());
        };

        return bookings.stream().map(this::mapToBookingResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasActiveBooking(Long freightId) {
        return bookingRepository.existsByCargoRequest_IdAndBookingStatusIn(
                freightId, 
                Arrays.asList(Booking.BookingStatus.PENDING, Booking.BookingStatus.ACCEPTED)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminUnpaidBookingResponse> getUnpaidBookings(User adminUser) {
        log.info("Admin {} fetching unpaid bookings", adminUser.getId());
        if (adminUser.getRole() != User.Role.ADMIN) {
            throw new AuthorizationException("Only administrators can view unpaid bookings");
        }

        List<Booking> unpaidList = bookingRepository.findUnpaidBookings();
        return unpaidList.stream()
                .map(this::mapToAdminUnpaidResponse)
                .collect(Collectors.toList());
    }

    private AdminUnpaidBookingResponse mapToAdminUnpaidResponse(Booking b) {
        String route;
        AdminUnpaidBookingResponse.PartyInfo shipper;
        if (b.getCargoRequest() != null) {
            route = b.getCargoRequest().getPickupLocation() + " → " + b.getCargoRequest().getDestination();
            User s = b.getCargoRequest().getShipper();
            shipper = AdminUnpaidBookingResponse.PartyInfo.builder()
                    .name(s != null && s.getFullName() != null ? s.getFullName() : (s != null && s.getEmail() != null ? s.getEmail() : "Unknown"))
                    .phone(s != null && s.getPhone() != null ? s.getPhone() : "")
                    .build();
        } else {
            route = "Booking #" + b.getId();
            shipper = AdminUnpaidBookingResponse.PartyInfo.builder().name("Unknown").phone("").build();
        }

        AdminUnpaidBookingResponse.PartyInfo driver;
        if (b.getDriver() != null) {
            User d = b.getDriver();
            driver = AdminUnpaidBookingResponse.PartyInfo.builder()
                    .name(d.getFullName() != null ? d.getFullName() : (d.getEmail() != null ? d.getEmail() : "Unknown"))
                    .phone(d.getPhone() != null ? d.getPhone() : "")
                    .build();
        } else {
            driver = AdminUnpaidBookingResponse.PartyInfo.builder().name("Unknown").phone("").build();
        }

        String statusStr = b.getBookingStatus() != null ? b.getBookingStatus().name().toLowerCase() : "pending";

        return AdminUnpaidBookingResponse.builder()
                .id(b.getId())
                .estimatedPrice(b.getEstimatedPrice())
                .commissionFee(b.getCommissionFee())
                .bookingStatus(statusStr)
                .route(route)
                .shipper(shipper)
                .driver(driver)
                .createdAt(b.getCreatedAt())
                .build();
    }

    private BookingResponse mapToBookingResponse(Booking booking) {
        return BookingResponse.builder()
                .id(booking.getId())
                .freightId(booking.getCargoRequest().getId())
                .freightDescription(booking.getCargoRequest().getCargoDescription())
                .vehicleId(booking.getVehicle().getId())
                .vehiclePlateNumber(booking.getVehicle().getPlateNumber())
                .vehicleType(booking.getVehicle().getTruckType())
                .driverId(booking.getDriver().getId())
                .driverName(booking.getDriver().getFullName())
                .bidId(booking.getBid() != null ? booking.getBid().getId() : null)
                .bookingStatus(booking.getBookingStatus() != null ? booking.getBookingStatus().name().toLowerCase() : null)
                .estimatedPrice(booking.getEstimatedPrice())
                .commissionFee(booking.getCommissionFee())
                .createdAt(booking.getCreatedAt())
                .updatedAt(booking.getUpdatedAt())
                .build();
    }
}