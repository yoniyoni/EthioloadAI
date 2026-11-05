package com.ethioloadai.trip.service;

import com.ethioloadai.booking.entity.Booking;
import com.ethioloadai.carrier.company.entity.CarrierCompany;
import com.ethioloadai.carrier.team.entity.CompanyMembership;
import com.ethioloadai.carrier.team.entity.CompanyRole;
import com.ethioloadai.carrier.team.service.CompanyContext;
import com.ethioloadai.carrier.team.service.CompanyContextService;
import com.ethioloadai.freight.entity.Freight;
import com.ethioloadai.freight.service.FreightVisibilityProperties;
import com.ethioloadai.trip.dto.TripResponse;
import com.ethioloadai.trip.entity.Trip;
import com.ethioloadai.trip.mapper.TripMapper;
import com.ethioloadai.trip.repository.TripRepository;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.vehicle.entity.Vehicle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyTripServiceTest {

    @Mock private TripRepository tripRepository;
    @Mock private CompanyContextService companyContextService;
    @Mock private FreightVisibilityProperties visibilityProperties;

    private CompanyTripService service;
    private User caller;

    @BeforeEach
    void setUp() {
        service = new CompanyTripService(
                tripRepository, companyContextService, visibilityProperties, new TripMapper());
        caller = User.builder().id(2L).role(User.Role.FLEET_OWNER).build();
    }

    @Test
    void dispatcherTripProjectionIncludesCompanyIdAndRedactsPrivateAndFinancialFields() {
        when(visibilityProperties.getCarrierHistoryDays()).thenReturn(90L);
        resolveAs(CompanyRole.DISPATCHER);
        var pageable = PageRequest.of(0, 20);
        when(tripRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(trip()), pageable, 1));

        TripResponse response = service.getTrips(
                10L, List.of(Trip.TripStatus.ONGOING), Trip.TripType.SINGLE, pageable, caller)
                .getContent().get(0);

        assertThat(response.getCarrierCompanyId()).isEqualTo(10L);
        assertThat(response.getBooking().getEstimatedPrice()).isNull();
        assertThat(response.getBooking().getCommissionFee()).isNull();
        assertThat(response.getDriver().getPhone()).isNull();
        assertThat(response.getShipper().getPhone()).isNull();
        verify(companyContextService).resolveForCompany(10L, caller);
    }

    @Test
    void fleetManagerTripProjectionMayIncludeFinancialFieldsButNeverContactPhones() {
        when(visibilityProperties.getCarrierHistoryDays()).thenReturn(90L);
        resolveAs(CompanyRole.FLEET_MANAGER);
        var pageable = PageRequest.of(0, 20);
        when(tripRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(trip()), pageable, 1));

        TripResponse response = service.getTrips(
                10L, List.of(), null, pageable, caller).getContent().get(0);

        assertThat(response.getBooking().getEstimatedPrice()).isEqualByComparingTo("500.00");
        assertThat(response.getBooking().getCommissionFee()).isEqualByComparingTo("25.00");
        assertThat(response.getDriver().getPhone()).isNull();
        assertThat(response.getShipper().getPhone()).isNull();
    }

    @Test
    void detailLookupDoesNotReturnTripOutsideCompanyScope() {
        when(visibilityProperties.getCarrierHistoryDays()).thenReturn(90L);
        resolveAs(CompanyRole.CARRIER_OWNER);
        when(tripRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 1), 0));

        assertThatThrownBy(() -> service.getTrip(10L, 999L, caller))
                .isInstanceOf(com.ethioloadai.trip.exception.TripNotFoundException.class);
    }

    @Test
    void globalFleetOwnerProjectionAlsoRedactsCompanyFinancialAndContactFields() {
        TripResponse response = new TripMapper().toCallerResponse(trip(), true);

        assertThat(response.getBooking().getEstimatedPrice()).isNull();
        assertThat(response.getBooking().getCommissionFee()).isNull();
        assertThat(response.getDriver().getPhone()).isNull();
        assertThat(response.getShipper().getPhone()).isNull();
    }

    private void resolveAs(CompanyRole role) {
        CompanyMembership membership = new CompanyMembership();
        membership.setRole(role);
        when(companyContextService.resolveForCompany(10L, caller))
                .thenReturn(new CompanyContext(
                        membership, CarrierCompany.builder().id(10L).build()));
    }

    private Trip trip() {
        User driver = User.builder().id(5L).role(User.Role.DRIVER)
                .fullName("Driver").phone("driver-phone").build();
        User shipper = User.builder().id(6L).role(User.Role.SHIPPER)
                .fullName("Shipper").phone("shipper-phone").build();
        Freight freight = Freight.builder().id(30L).shipper(shipper).build();
        Vehicle vehicle = Vehicle.builder().id(40L).plateNumber("ABC-123").truckType("Truck").build();
        Booking booking = Booking.builder()
                .id(50L)
                .carrierCompanyId(10L)
                .cargoRequest(freight)
                .driver(driver)
                .vehicle(vehicle)
                .bookingStatus(Booking.BookingStatus.ACCEPTED)
                .estimatedPrice(new BigDecimal("500.00"))
                .commissionFee(new BigDecimal("25.00"))
                .build();
        return Trip.builder()
                .id(60L)
                .tripStatus(Trip.TripStatus.ONGOING)
                .tripType(Trip.TripType.SINGLE)
                .booking(booking)
                .build();
    }
}
