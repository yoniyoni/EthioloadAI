package com.ethioloadai.trip.service;

import com.ethioloadai.booking.entity.Booking;
import com.ethioloadai.booking.repository.BookingRepository;
import com.ethioloadai.freight.entity.Freight;
import com.ethioloadai.trip.entity.Trip;
import com.ethioloadai.trip.exception.TripValidationException;
import com.ethioloadai.trip.repository.TripRepository;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.vehicle.entity.Vehicle;
import com.ethioloadai.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TripInvariantServiceImplTest {

    @Mock private TripRepository tripRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private VehicleRepository vehicleRepository;

    private TripInvariantServiceImpl service;
    private Booking currentBooking;
    private Vehicle vehicle;

    @BeforeEach
    void setUp() {
        service = new TripInvariantServiceImpl(tripRepository, bookingRepository, vehicleRepository);

        User driver = User.builder().id(12L).role(User.Role.DRIVER).build();
        vehicle = Vehicle.builder().id(34L).userId(driver.getId()).build();
        Freight freight = Freight.builder().id(56L).build();
        currentBooking = Booking.builder()
                .id(78L)
                .cargoRequest(freight)
                .driver(driver)
                .vehicle(vehicle)
                .build();

        when(vehicleRepository.findByUserId(driver.getId())).thenReturn(List.of(vehicle));
    }

    @Test
    void succeedsWhenNoBookingOrTripConflictExists() {
        when(tripRepository.findByDriverIdAndStatus(12L, Trip.TripStatus.ONGOING))
                .thenReturn(List.of());
        when(bookingRepository.findVehicleConflict(
                eq(56L), eq(78L), eq(34L), eq(eligibleStatuses()), eq(Booking.BookingStatus.ACCEPTED),
                argThat(TripInvariantServiceImplTest::isSingleResultPage)))
                .thenReturn(List.of());

        assertThatCode(() -> service.enforceVehicleConsistency(currentBooking))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsPendingBookingConflictWithItsId() {
        rejectsConflict(Booking.BookingStatus.PENDING, 91L);
    }

    @Test
    void rejectsAcceptedBookingConflictWithItsId() {
        rejectsConflict(Booking.BookingStatus.ACCEPTED, 92L);
    }

    private void rejectsConflict(Booking.BookingStatus status, Long conflictingBookingId) {
        Booking conflict = Booking.builder()
                .id(conflictingBookingId)
                .bookingStatus(status)
                .build();
        when(bookingRepository.findVehicleConflict(
                eq(56L), eq(78L), eq(34L), eq(eligibleStatuses()), eq(Booking.BookingStatus.ACCEPTED),
                argThat(TripInvariantServiceImplTest::isSingleResultPage)))
                .thenReturn(List.of(conflict));

        assertThatThrownBy(() -> service.enforceVehicleConsistency(currentBooking))
                .isInstanceOf(TripValidationException.class)
                .hasMessageContaining("vehicle 34 is already in use by booking " + conflictingBookingId);
        verify(bookingRepository).findVehicleConflict(
                eq(56L), eq(78L), eq(34L), eq(eligibleStatuses()), eq(Booking.BookingStatus.ACCEPTED),
                argThat(TripInvariantServiceImplTest::isSingleResultPage));
    }

    private static List<Booking.BookingStatus> eligibleStatuses() {
        return List.of(Booking.BookingStatus.ACCEPTED, Booking.BookingStatus.PENDING);
    }

    private static boolean isSingleResultPage(Pageable pageable) {
        return pageable.getPageNumber() == 0 && pageable.getPageSize() == 1;
    }
}
