package com.ethioloadai.booking;

import com.ethioloadai.booking.entity.Booking;
import com.ethioloadai.booking.exception.BookingNotFoundException;
import com.ethioloadai.booking.repository.BookingRepository;
import com.ethioloadai.booking.service.BookingServiceImpl;
import com.ethioloadai.carrier.team.service.FleetOwnerHistoryScopeService;
import com.ethioloadai.exception.AuthorizationException;
import com.ethioloadai.freight.entity.Freight;
import com.ethioloadai.freight.service.FreightVisibilityProperties;
import com.ethioloadai.trip.entity.Trip;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.vehicle.entity.Vehicle;
import com.ethioloadai.bid.repository.BidRepository;
import com.ethioloadai.bid.service.CommissionService;
import com.ethioloadai.freight.repository.FreightRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceVisibilityTest {

    @Mock private BidRepository bidRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private FreightRepository freightRepository;
    @Mock private CommissionService commissionService;
    @Mock private FleetOwnerHistoryScopeService fleetOwnerHistoryScope;
    @Mock private FreightVisibilityProperties visibilityProperties;

    @InjectMocks private BookingServiceImpl service;

    private User driver;
    private User otherDriver;
    private User fleetOwner;
    private Booking booking;
    private Freight freight;

    @BeforeEach
    void setUp() {
        driver = user(1L, User.Role.DRIVER);
        otherDriver = user(2L, User.Role.DRIVER);
        fleetOwner = user(3L, User.Role.FLEET_OWNER);
        User shipper = user(4L, User.Role.SHIPPER);
        freight = Freight.builder()
                .id(20L)
                .shipper(shipper)
                .status(Freight.FreightStatus.IN_TRANSIT)
                .updatedAt(LocalDateTime.now())
                .build();
        booking = Booking.builder()
                .id(30L)
                .cargoRequest(freight)
                .driver(driver)
                .bookingStatus(Booking.BookingStatus.ACCEPTED)
                .updatedAt(LocalDateTime.now())
                .carrierCompanyId(40L)
                .vehicle(Vehicle.builder().id(50L).plateNumber("ABC-123").truckType("Truck").build())
                .estimatedPrice(new java.math.BigDecimal("500.00"))
                .commissionFee(new java.math.BigDecimal("25.00"))
                .build();
    }

    @Test
    void activeBookingCanBeReadButAnotherDriversBookingIdCannotBeUsed() {
        when(bookingRepository.findByIdWithAssociations(30L)).thenReturn(Optional.of(booking));

        assertThat(service.getBookingWithAuthorization(30L, driver)).isSameAs(booking);
        assertThatThrownBy(() -> service.getBookingWithAuthorization(30L, otherDriver))
                .isInstanceOf(AuthorizationException.class);
    }

    @Test
    void completedBookingIsVisibleAtDriverRetentionBoundaryAndDeniedAfterward() {
        freight.setStatus(Freight.FreightStatus.COMPLETED);
        booking.setBookingStatus(Booking.BookingStatus.COMPLETED);
        booking.setTrip(completedTrip(LocalDateTime.now().minusDays(30).plusMinutes(1)));
        when(visibilityProperties.getDriverHistoryDays()).thenReturn(30L);
        when(bookingRepository.findByIdWithAssociations(30L)).thenReturn(Optional.of(booking));

        assertThat(service.getBookingWithAuthorization(30L, driver)).isSameAs(booking);

        booking.setTrip(completedTrip(LocalDateTime.now().minusDays(30).minusMinutes(1)));
        assertThatThrownBy(() -> service.getBookingWithAuthorization(30L, driver))
                .isInstanceOf(BookingNotFoundException.class);
    }

    @Test
    void completedCarrierBookingUsesNinetyDayRetentionBoundary() {
        freight.setStatus(Freight.FreightStatus.COMPLETED);
        booking.setBookingStatus(Booking.BookingStatus.COMPLETED);
        booking.setTrip(completedTrip(LocalDateTime.now().minusDays(90).plusMinutes(1)));
        when(fleetOwnerHistoryScope.getApprovedCompanyIds(fleetOwner)).thenReturn(List.of(40L));
        when(bookingRepository.existsByIdAndCarrierCompanyIds(30L, List.of(40L))).thenReturn(true);
        when(visibilityProperties.getCarrierHistoryDays()).thenReturn(90L);
        when(bookingRepository.findByIdWithAssociations(30L)).thenReturn(Optional.of(booking));

        assertThat(service.getBookingWithAuthorization(30L, fleetOwner)).isSameAs(booking);

        booking.setTrip(completedTrip(LocalDateTime.now().minusDays(90).minusMinutes(1)));
        assertThatThrownBy(() -> service.getBookingWithAuthorization(30L, fleetOwner))
                .isInstanceOf(BookingNotFoundException.class);
    }

    @Test
    void globalFleetOwnerBookingProjectionDoesNotExposeFinancialFields() {
        when(fleetOwnerHistoryScope.getApprovedCompanyIds(fleetOwner)).thenReturn(List.of(40L));
        when(visibilityProperties.getCarrierHistoryDays()).thenReturn(90L);
        when(bookingRepository.findAll(
                org.mockito.ArgumentMatchers.<Specification<Booking>>any(),
                any(Sort.class))).thenReturn(List.of(booking));

        var response = service.getBookingsForCaller(fleetOwner).get(0);

        assertThat(response.getEstimatedPrice()).isNull();
        assertThat(response.getCommissionFee()).isNull();
    }

    @Test
    void cancelledBookingRetentionUsesItsCancellationTimestamp() {
        booking.setBookingStatus(Booking.BookingStatus.CANCELLED);
        booking.setUpdatedAt(LocalDateTime.now().minusDays(31));
        when(visibilityProperties.getDriverHistoryDays()).thenReturn(30L);
        when(bookingRepository.findByIdWithAssociations(30L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.getBookingTrip(30L, driver))
                .isInstanceOf(BookingNotFoundException.class);
    }

    @Test
    void completedBookingExpiresIndependentlyOfStaleFreightStatus() {
        freight.setStatus(Freight.FreightStatus.IN_TRANSIT);
        booking.setBookingStatus(Booking.BookingStatus.COMPLETED);
        booking.setUpdatedAt(LocalDateTime.now().minusDays(31));
        when(visibilityProperties.getDriverHistoryDays()).thenReturn(30L);
        when(bookingRepository.findByIdWithAssociations(30L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.getBookingWithAuthorization(30L, driver))
                .isInstanceOf(BookingNotFoundException.class);
    }

    @Test
    void zeroDayDriverHistoryDeniesCompletedBooking() {
        booking.setBookingStatus(Booking.BookingStatus.COMPLETED);
        booking.setUpdatedAt(LocalDateTime.now());
        when(visibilityProperties.getDriverHistoryDays()).thenReturn(0L);
        when(bookingRepository.findByIdWithAssociations(30L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.getBookingWithAuthorization(30L, driver))
                .isInstanceOf(BookingNotFoundException.class);
    }

    @Test
    void completedTripWithNullEndTimeDeniesDriverBookingAccess() {
        freight.setStatus(Freight.FreightStatus.IN_TRANSIT);
        booking.setBookingStatus(Booking.BookingStatus.DELIVERED);
        booking.setTrip(completedTrip(null));
        when(bookingRepository.findByIdWithAssociations(30L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.getBookingWithAuthorization(30L, driver))
                .isInstanceOf(BookingNotFoundException.class);
    }

    @Test
    void completedTripWithNullEndTimeDoesNotRestrictShipperOrAdminBookingAccess() {
        freight.setStatus(Freight.FreightStatus.IN_TRANSIT);
        booking.setBookingStatus(Booking.BookingStatus.DELIVERED);
        booking.setTrip(completedTrip(null));
        User shipper = user(4L, User.Role.SHIPPER);
        User admin = user(5L, User.Role.ADMIN);
        when(bookingRepository.findByIdWithAssociations(30L)).thenReturn(Optional.of(booking));

        assertThat(service.getBookingWithAuthorization(30L, shipper)).isSameAs(booking);
        assertThat(service.getBookingWithAuthorization(30L, admin)).isSameAs(booking);
    }

    @Test
    void driverListsAndPagesUseRepositoryHistorySpecifications() {
        PageRequest pageable = PageRequest.of(0, 10);
        when(visibilityProperties.getDriverHistoryDays()).thenReturn(30L);
        when(bookingRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of());
        when(bookingRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        assertThat(service.getBookingsForCaller(driver)).isEmpty();
        assertThat(service.getBookingsPageForCaller(driver, pageable, null).content()).isEmpty();

        verify(bookingRepository).findAll(any(Specification.class), any(Sort.class));
        verify(bookingRepository).findAll(any(Specification.class), eq(pageable));
        verify(bookingRepository, never()).findByDriver_Id(driver.getId());
        verify(bookingRepository, never()).findPageIdsByDriver_Id(driver.getId(), pageable);
    }

    @Test
    void inactiveFleetMembershipCannotReadBookingByIdOrListHistoricalCompanyRecords() {
        when(fleetOwnerHistoryScope.getApprovedCompanyIds(fleetOwner)).thenReturn(List.of());
        when(bookingRepository.findByIdWithAssociations(30L)).thenReturn(Optional.of(booking));
        when(bookingRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of());

        assertThatThrownBy(() -> service.getBookingWithAuthorization(30L, fleetOwner))
                .isInstanceOf(AuthorizationException.class);
        assertThat(service.getBookingsForCaller(fleetOwner)).isEmpty();
    }

    private Trip completedTrip(LocalDateTime endTime) {
        return Trip.builder()
                .id(50L)
                .booking(booking)
                .tripStatus(Trip.TripStatus.COMPLETED)
                .endTime(endTime)
                .build();
    }

    private User user(Long id, User.Role role) {
        return User.builder().id(id).role(role).build();
    }
}
