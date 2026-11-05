package com.ethioloadai.booking.service;

import com.ethioloadai.bid.entity.Bid;
import com.ethioloadai.bid.exception.BidNotFoundException;
import com.ethioloadai.bid.exception.BidStatusException;
import com.ethioloadai.bid.repository.BidRepository;
import com.ethioloadai.bid.service.CommissionService;
import com.ethioloadai.booking.entity.Booking;
import com.ethioloadai.trip.entity.Trip;
import com.ethioloadai.booking.dto.BookingPageResponse;
import com.ethioloadai.booking.dto.BookingResponse;
import com.ethioloadai.booking.dto.BookingTripResponse;
import com.ethioloadai.booking.exception.BookingNotFoundException;
import com.ethioloadai.booking.repository.BookingRepository;
import com.ethioloadai.booking.repository.BookingRepository.BookingIdProjection;
import com.ethioloadai.booking.repository.BookingVisibilitySpecifications;
import com.ethioloadai.carrier.team.service.FleetOwnerHistoryScopeService;
import com.ethioloadai.exception.AuthorizationException;
import com.ethioloadai.freight.entity.Freight;
import com.ethioloadai.freight.repository.FreightRepository;
import com.ethioloadai.freight.service.FreightVisibilityProperties;
import com.ethioloadai.trip.entity.Trip;
import com.ethioloadai.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.ethioloadai.booking.dto.AdminUnpaidBookingResponse;
import com.ethioloadai.booking.dto.AdminUnpaidBookingsListResponse;
import com.ethioloadai.booking.dto.BookingResponse;
import org.hibernate.Hibernate;
import java.math.BigDecimal;
import java.util.Arrays;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {
    private static final Logger log = LoggerFactory.getLogger(BookingServiceImpl.class);

    private final BidRepository bidRepository;
    private final BookingRepository bookingRepository;
    private final FreightRepository freightRepository;
    private final CommissionService commissionService;
    private final FleetOwnerHistoryScopeService fleetOwnerHistoryScope;
    private final FreightVisibilityProperties visibilityProperties;

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Booking acceptBid(Long bidId, User shipper) {
        log.info("Accepting bid {} by shipper {}", bidId, shipper.getId());

        Long freightId = bidRepository.findFreightIdById(bidId)
                .orElseThrow(() -> new BidNotFoundException(bidId));
        Freight freight = freightRepository.findByIdForUpdate(freightId)
                .orElseThrow(() -> new BidStatusException("Freight is no longer available for booking"));
        Bid bid = bidRepository.findByIdForUpdate(bidId).orElse(null);
        if (bid == null || !bid.getCargoRequest().getId().equals(freight.getId())) {
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

        Long freightId = bidRepository.findFreightIdById(bidId)
                .orElseThrow(() -> new BidNotFoundException(bidId));
        Freight freight = freightRepository.findByIdForUpdate(freightId)
                .orElseThrow(() -> new BidStatusException("Freight is no longer available for booking"));
        Bid bid = bidRepository.findByIdForUpdate(bidId).orElse(null);
        if (bid == null || !bid.getCargoRequest().getId().equals(freight.getId())) {
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

        // 3. Persist the accepted driver as the authoritative freight assignment
        freight.setStatus(Freight.FreightStatus.MATCHED);
        freight.setMatchedDriver(acceptedBid.getDriver());
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
        boolean isDriver = user.getRole() == User.Role.DRIVER
                && booking.getDriver().getId().equals(user.getId());
        boolean isShipper = user.getRole() == User.Role.SHIPPER
                && booking.getCargoRequest().getShipper().getId().equals(user.getId());
        boolean isAdmin = user.getRole() == User.Role.ADMIN;
        List<Long> companyIds = user.getRole() == User.Role.FLEET_OWNER
                ? fleetOwnerHistoryScope.getApprovedCompanyIds(user)
                : List.of();
        boolean isFleetCompanyMember = !companyIds.isEmpty()
                && bookingRepository.existsByIdAndCarrierCompanyIds(bookingId, companyIds);

        if (!isDriver && !isShipper && !isAdmin && !isFleetCompanyMember) {
            throw new AuthorizationException("You are not authorized to view this booking");
        }
        if (!hasHistoricalAccess(booking, user)) {
            throw new BookingNotFoundException(bookingId);
        }

        return booking;
    }

    @Override
    @Transactional(readOnly = true)
    public BookingResponse getBookingResponseWithAuthorization(Long bookingId, User user) {
        log.info("Getting booking response {} for user {}", bookingId, user.getId());
        
        Booking booking = getBookingWithAuthorization(bookingId, user);
        return mapToBookingResponse(booking, user.getRole() != User.Role.FLEET_OWNER);
    }

    @Override
    @Transactional(readOnly = true)
    public BookingTripResponse getBookingTrip(Long bookingId, User user) {
        Booking booking = getBookingWithAuthorization(bookingId, user);
        Trip trip = booking.getTrip();
        String serviceType = booking.getCargoRequest().getServiceType() == null
                ? null
                : booking.getCargoRequest().getServiceType().name().toLowerCase(Locale.ROOT);

        if (trip == null) {
            return new BookingTripResponse(
                    booking.getId(),
                    booking.getCargoRequest().getId(),
                    booking.getBookingStatus().name().toLowerCase(Locale.ROOT),
                    null,
                    null,
                    null,
                    serviceType);
        }

        if (trip.getBooking() == null
                || !booking.getId().equals(trip.getBooking().getId())
                || trip.getBooking().getCargoRequest() == null
                || !booking.getCargoRequest().getId().equals(
                        trip.getBooking().getCargoRequest().getId())
                || trip.getDriver() == null
                || !booking.getDriver().getId().equals(trip.getDriver().getId())) {
            log.error("Booking-trip relationship integrity failure for booking {}", bookingId);
            throw new IllegalStateException("Booking-trip relationship is inconsistent");
        }

        return new BookingTripResponse(
                booking.getId(),
                booking.getCargoRequest().getId(),
                booking.getBookingStatus().name().toLowerCase(Locale.ROOT),
                trip.getId(),
                trip.getTripStatus().name().toLowerCase(Locale.ROOT),
                trip.getTripType().name().toLowerCase(Locale.ROOT),
                serviceType);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookingResponse> getBookingsForCaller(User user) {
        return bookingRepository.findAll(callerVisibility(user),
                        Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")))
                .stream()
                .map(booking -> mapToBookingResponse(
                        booking, user.getRole() != User.Role.FLEET_OWNER))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BookingPageResponse getBookingsPageForCaller(User user, Pageable pageable, Long adminDriverId) {
        Long totalBookings = null;
        if (adminDriverId != null) {
            if (user.getRole() != User.Role.ADMIN) {
                throw new AuthorizationException("Only administrators can filter bookings by driver");
            }
            Slice<BookingIdProjection> bookingIds =
                    bookingRepository.findPageIdsByDriverIdForAdmin(adminDriverId, pageable);
            totalBookings = bookingRepository.countByDriver_IdForAdmin(adminDriverId);
            List<Long> orderedIds = bookingIds.getContent().stream()
                    .map(BookingIdProjection::getId)
                    .toList();
            List<BookingResponse> content = loadBookingResponses(orderedIds);
            return new BookingPageResponse(
                    content, bookingIds.getNumber(), bookingIds.getSize(), bookingIds.hasNext(), totalBookings);
        }

        Page<Booking> bookings = bookingRepository.findAll(callerVisibility(user), pageable);
        List<BookingResponse> content = bookings.getContent().stream()
                .map(booking -> mapToBookingResponse(
                        booking, user.getRole() != User.Role.FLEET_OWNER))
                .toList();
        return new BookingPageResponse(
                content, bookings.getNumber(), bookings.getSize(), bookings.hasNext(), totalBookings);
    }

    private List<BookingResponse> loadBookingResponses(List<Long> orderedIds) {
        if (orderedIds.isEmpty()) {
            return List.of();
        }
        Map<Long, Booking> bookingsById = bookingRepository.findByIdIn(orderedIds).stream()
                .collect(Collectors.toMap(Booking::getId, booking -> booking));
        return orderedIds.stream()
                .map(bookingsById::get)
                .map(this::mapToBookingResponse)
                .toList();
    }

    private Specification<Booking> callerVisibility(User user) {
        List<Long> companyIds = user.getRole() == User.Role.FLEET_OWNER
                ? fleetOwnerHistoryScope.getApprovedCompanyIds(user)
                : List.of();
        Specification<Booking> specification =
                BookingVisibilitySpecifications.callerScope(user, companyIds);
        if (user.getRole() == User.Role.DRIVER || user.getRole() == User.Role.FLEET_OWNER) {
            long historyDays = user.getRole() == User.Role.DRIVER
                    ? visibilityProperties.getDriverHistoryDays()
                    : visibilityProperties.getCarrierHistoryDays();
            specification = specification.and(
                    BookingVisibilitySpecifications.withinHistoryWindow(historyDays, LocalDateTime.now()));
        }
        return specification;
    }

    private boolean hasHistoricalAccess(Booking booking, User user) {
        if (user.getRole() != User.Role.DRIVER && user.getRole() != User.Role.FLEET_OWNER) {
            return true;
        }

        Freight freight = booking.getCargoRequest();
        Trip trip = booking.getTrip();
        boolean bookingCancelled = booking.getBookingStatus() == Booking.BookingStatus.CANCELLED;
        boolean freightCancelled = freight.getStatus() == Freight.FreightStatus.CANCELLED;
        boolean bookingCompleted = booking.getBookingStatus() == Booking.BookingStatus.COMPLETED;
        boolean freightCompleted = freight.getStatus() == Freight.FreightStatus.COMPLETED;
        boolean tripCompleted = trip != null && trip.getTripStatus() == Trip.TripStatus.COMPLETED;
        if (tripCompleted && trip.getEndTime() == null) {
            return false;
        }
        if (!bookingCancelled && !freightCancelled && !bookingCompleted && !freightCompleted && !tripCompleted) {
            return true;
        }

        long historyDays = user.getRole() == User.Role.DRIVER
                ? visibilityProperties.getDriverHistoryDays()
                : visibilityProperties.getCarrierHistoryDays();
        if (historyDays <= 0) {
            return false;
        }
        LocalDateTime terminalAt;
        if (freightCancelled) {
            terminalAt = freight.getUpdatedAt();
        } else if (tripCompleted) {
            terminalAt = trip.getEndTime();
        } else if (bookingCancelled || bookingCompleted) {
            terminalAt = booking.getUpdatedAt();
        } else {
            terminalAt = freight.getUpdatedAt();
        }
        LocalDateTime now = LocalDateTime.now();
        return withinHistoryWindow(terminalAt, now.minusDays(historyDays), now);
    }

    private boolean withinHistoryWindow(LocalDateTime timestamp, LocalDateTime cutoff, LocalDateTime now) {
        return timestamp != null && !timestamp.isBefore(cutoff) && !timestamp.isAfter(now);
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
    public AdminUnpaidBookingsListResponse getUnpaidBookings(User adminUser, Pageable pageable) {
        log.info("Admin {} fetching unpaid bookings", adminUser.getId());
        if (adminUser.getRole() != User.Role.ADMIN) {
            throw new AuthorizationException("Only administrators can view unpaid bookings");
        }

        Page<Booking> unpaidPage = bookingRepository.findUnpaidBookings(
                Booking.BookingStatus.CANCELLED, pageable);
        List<AdminUnpaidBookingResponse> bookings = unpaidPage.getContent().stream()
                .map(this::mapToAdminUnpaidResponse)
                .collect(Collectors.toList());
        return new AdminUnpaidBookingsListResponse(
                bookings,
                unpaidPage.getTotalElements(),
                unpaidPage.getNumber(),
                unpaidPage.getSize(),
                unpaidPage.hasNext());
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
        return mapToBookingResponse(booking, true);
    }

    private BookingResponse mapToBookingResponse(Booking booking, boolean includeFinancial) {
        return BookingResponse.builder()
                .id(booking.getId())
                .freightId(booking.getCargoRequest().getId())
                .freightDescription(booking.getCargoRequest().getCargoDescription())
                .vehicleId(booking.getVehicle().getId())
                .vehiclePlateNumber(booking.getVehicle().getPlateNumber())
                .vehicleType(booking.getVehicle().getTruckType())
                .driverId(booking.getDriver().getId())
                .driverName(booking.getDriver().getFullName())
                .tripId(booking.getTrip() != null ? booking.getTrip().getId() : null)
                .tripStatus(booking.getTrip() != null
                        ? booking.getTrip().getTripStatus().name().toLowerCase(Locale.ROOT) : null)
                .bidId(booking.getBid() != null ? booking.getBid().getId() : null)
                .bookingStatus(booking.getBookingStatus() != null ? booking.getBookingStatus().name().toLowerCase() : null)
                .estimatedPrice(includeFinancial ? booking.getEstimatedPrice() : null)
                .commissionFee(includeFinancial ? booking.getCommissionFee() : null)
                .createdAt(booking.getCreatedAt())
                .updatedAt(booking.getUpdatedAt())
                .build();
    }
}