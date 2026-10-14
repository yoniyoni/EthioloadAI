package com.ethioloadai.dashboard.service;

import com.ethioloadai.booking.entity.Booking;
import com.ethioloadai.booking.dto.BookingResponse;
import com.ethioloadai.booking.repository.BookingRepository;
import com.ethioloadai.dashboard.dto.DriverDashboardResponse;
import com.ethioloadai.dashboard.dto.DashboardSummaryResponse;
import com.ethioloadai.exception.AuthorizationException;
import com.ethioloadai.freight.dto.FreightResponse;
import com.ethioloadai.freight.entity.Freight;
import com.ethioloadai.freight.mapper.FreightMapper;
import com.ethioloadai.freight.repository.FreightRepository;
import com.ethioloadai.trip.entity.Trip;
import com.ethioloadai.trip.repository.TripRepository;
import com.ethioloadai.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class DashboardService {
    private static final int RECENT_FREIGHT_LIMIT = 5;
    private static final int RECENT_OPERATIONAL_FREIGHT_LIMIT = 4;
    private static final int RECENT_ACTIVITY_LIMIT = 3;

    private static final List<Freight.FreightStatus> OPERATIONAL_FREIGHT_STATUSES = List.of(
            Freight.FreightStatus.POSTED,
            Freight.FreightStatus.MATCHED,
            Freight.FreightStatus.IN_TRANSIT);
    private static final List<Booking.BookingStatus> INACTIVE_BOOKING_STATUSES = List.of(
            Booking.BookingStatus.COMPLETED,
            Booking.BookingStatus.CANCELLED);

    private final FreightRepository freightRepository;
    private final BookingRepository bookingRepository;
    private final TripRepository tripRepository;
    private final FreightMapper freightMapper;

    @Transactional(readOnly = true)
    public DashboardSummaryResponse getSummary(User user) {
        if (user.getRole() != User.Role.SHIPPER && user.getRole() != User.Role.ADMIN) {
            throw new AuthorizationException(
                    "Only shippers and administrators can view this dashboard summary");
        }

        Long userId = user.getId();
        List<FreightResponse> recentFreight = freightRepository.findByShipper_Id(
                        userId, PageRequest.of(0, RECENT_FREIGHT_LIMIT,
                                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))))
                .stream()
                .map(freightMapper::toResponse)
                .toList();
        List<FreightResponse> recentOperationalFreight = freightRepository.findByShipper_IdAndStatusIn(
                        userId, OPERATIONAL_FREIGHT_STATUSES,
                        PageRequest.of(0, RECENT_OPERATIONAL_FREIGHT_LIMIT,
                                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))))
                .stream()
                .map(freightMapper::toResponse)
                .toList();

        boolean shipper = user.getRole() == User.Role.SHIPPER;
        List<DashboardSummaryResponse.BookingActivity> recentBookings = shipper
                ? bookingRepository.findRecentDashboardBookings(userId, PageRequest.of(
                                0, RECENT_ACTIVITY_LIMIT))
                        .stream()
                        .map(row -> new DashboardSummaryResponse.BookingActivity(
                                row.getId(), row.getBookingStatus().name().toLowerCase(Locale.ROOT)))
                        .toList()
                : List.of();
        List<DashboardSummaryResponse.TripActivity> recentTrips = shipper
                ? tripRepository.findRecentDashboardTrips(userId, PageRequest.of(
                                0, RECENT_ACTIVITY_LIMIT))
                        .stream()
                        .map(row -> new DashboardSummaryResponse.TripActivity(
                                row.getId(), row.getTripStatus().name()))
                        .toList()
                : List.of();

        return new DashboardSummaryResponse(
                freightRepository.countByShipper_Id(userId),
                freightRepository.countByShipper_IdAndStatusIn(userId, OPERATIONAL_FREIGHT_STATUSES),
                freightRepository.countByShipper_IdAndStatus(userId, Freight.FreightStatus.COMPLETED),
                freightRepository.countByShipper_IdAndStatus(userId, Freight.FreightStatus.CANCELLED),
                shipper ? bookingRepository.countActiveByShipperId(userId, INACTIVE_BOOKING_STATUSES) : 0,
                shipper ? tripRepository.countActiveByShipperId(userId, Trip.TripStatus.COMPLETED) : 0,
                recentFreight,
                recentOperationalFreight,
                recentBookings,
                recentTrips,
                shipper ? bookingRepository.countByShipperId(userId) : 0,
                shipper ? bookingRepository.countByShipperIdAndStatus(
                        userId, Booking.BookingStatus.PENDING) : 0);
    }

    @Transactional(readOnly = true)
    public DriverDashboardResponse getDriverSummary(User user) {
        if (user.getRole() != User.Role.DRIVER) {
            throw new AuthorizationException("Only drivers can view this dashboard summary");
        }

        BookingRepository.DriverDashboardCountsProjection counts =
                bookingRepository.findDriverDashboardCounts(
                        user.getId(),
                        Booking.BookingStatus.CONFIRMED,
                        Booking.BookingStatus.COMPLETED,
                        Booking.BookingStatus.PENDING);
        List<BookingResponse> pendingBookingRows = bookingRepository
                .findTop3ByDriver_IdAndBookingStatusOrderByCreatedAtDescIdDesc(
                        user.getId(), Booking.BookingStatus.PENDING)
                .stream()
                .map(booking -> BookingResponse.builder()
                        .id(booking.getId())
                        .freightId(booking.getCargoRequest().getId())
                        .freightDescription(booking.getCargoRequest().getCargoDescription())
                        .vehicleId(booking.getVehicle().getId())
                        .vehiclePlateNumber(booking.getVehicle().getPlateNumber())
                        .vehicleType(booking.getVehicle().getTruckType())
                        .driverId(booking.getDriver().getId())
                        .driverName(booking.getDriver().getFullName())
                        .bidId(booking.getBid() == null ? null : booking.getBid().getId())
                        .bookingStatus(booking.getBookingStatus().name().toLowerCase(Locale.ROOT))
                        .estimatedPrice(booking.getEstimatedPrice())
                        .commissionFee(booking.getCommissionFee())
                        .createdAt(booking.getCreatedAt())
                        .updatedAt(booking.getUpdatedAt())
                        .build())
                .toList();

        return new DriverDashboardResponse(
                Objects.requireNonNullElse(counts.getTotalBookings(), 0L),
                Objects.requireNonNullElse(counts.getConfirmedBookings(), 0L),
                Objects.requireNonNullElse(counts.getCompletedBookings(), 0L),
                Objects.requireNonNullElse(counts.getPendingBookings(), 0L),
                pendingBookingRows);
    }
}
