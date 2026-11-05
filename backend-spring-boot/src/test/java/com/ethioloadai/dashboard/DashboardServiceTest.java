package com.ethioloadai.dashboard;

import com.ethioloadai.booking.entity.Booking;
import com.ethioloadai.booking.repository.BookingRepository;
import com.ethioloadai.dashboard.dto.DashboardSummaryResponse;
import com.ethioloadai.dashboard.service.DashboardService;
import com.ethioloadai.freight.repository.FreightRepository;
import com.ethioloadai.freight.entity.Freight;
import com.ethioloadai.freight.mapper.FreightMapper;
import com.ethioloadai.trip.entity.Trip;
import com.ethioloadai.trip.repository.TripRepository;
import com.ethioloadai.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {
    @Mock private FreightRepository freightRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private TripRepository tripRepository;
    @Mock private FreightMapper freightMapper;
    @InjectMocks private DashboardService dashboardService;

    @Test
    void summaryCountsConfirmedBookingsSeparatelyFromOtherOpenBookings() {
        User shipper = User.builder().id(17L).role(User.Role.SHIPPER).build();
        when(freightRepository.findByShipper_Id(eq(17L), any(Pageable.class))).thenReturn(List.of());
        when(freightRepository.findByShipper_IdAndStatusIn(eq(17L), anyCollection(), any(Pageable.class)))
                .thenReturn(List.of());
        when(bookingRepository.findRecentDashboardBookings(eq(17L), any(Pageable.class))).thenReturn(List.of());
        when(tripRepository.findRecentDashboardTrips(eq(17L), any(Pageable.class))).thenReturn(List.of());
        when(bookingRepository.countActiveByShipperId(eq(17L), anyCollection())).thenReturn(3L);
        when(bookingRepository.countByShipperIdAndStatus(17L, Booking.BookingStatus.CONFIRMED)).thenReturn(2L);
        when(bookingRepository.countByShipperIdAndStatus(17L, Booking.BookingStatus.PENDING)).thenReturn(1L);
        when(tripRepository.countActiveByShipperId(17L, Trip.TripStatus.COMPLETED)).thenReturn(1L);

        DashboardSummaryResponse summary = dashboardService.getSummary(shipper);

        assertThat(summary.activeBookings()).isEqualTo(3);
        assertThat(summary.confirmedBookings()).isEqualTo(2);
        verify(bookingRepository).countByShipperIdAndStatus(17L, Booking.BookingStatus.CONFIRMED);
    }

    @Test
    void adminSummaryDoesNotExposeShipperBookingCounts() {
        User admin = User.builder().id(23L).role(User.Role.ADMIN).build();
        when(freightRepository.findByShipper_Id(eq(23L), any(Pageable.class))).thenReturn(List.of());
        when(freightRepository.findByShipper_IdAndStatusIn(eq(23L), anyCollection(), any(Pageable.class)))
                .thenReturn(List.of());

        DashboardSummaryResponse summary = dashboardService.getSummary(admin);

        assertThat(summary.confirmedBookings()).isZero();
    }
}
