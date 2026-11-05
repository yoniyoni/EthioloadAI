package com.ethioloadai.booking;

import com.ethioloadai.booking.entity.Booking;
import com.ethioloadai.booking.repository.BookingRepository;
import com.ethioloadai.booking.service.CompanyBookingService;
import com.ethioloadai.carrier.company.entity.CarrierCompany;
import com.ethioloadai.carrier.team.entity.CompanyMembership;
import com.ethioloadai.carrier.team.entity.CompanyRole;
import com.ethioloadai.carrier.team.service.CompanyContext;
import com.ethioloadai.carrier.team.service.CompanyContextService;
import com.ethioloadai.freight.entity.Freight;
import com.ethioloadai.freight.service.FreightVisibilityProperties;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.vehicle.entity.Vehicle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyBookingServiceTest {

    @Mock private BookingRepository bookingRepository;
    @Mock private CompanyContextService companyContextService;
    @Mock private FreightVisibilityProperties visibilityProperties;

    private CompanyBookingService service;
    private User caller;

    @BeforeEach
    void setUp() {
        service = new CompanyBookingService(
                bookingRepository, companyContextService, visibilityProperties);
        caller = User.builder().id(2L).role(User.Role.FLEET_OWNER).build();
        when(visibilityProperties.getCarrierHistoryDays()).thenReturn(90L);
    }

    @Test
    void dispatcherGetsCompanyBookingOperationsWithoutFinancialFields() {
        resolveAs(CompanyRole.DISPATCHER);
        var pageable = PageRequest.of(0, 20);
        when(bookingRepository.findAll(
                any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(booking()), pageable, 1));

        var page = service.getBookings(10L, Booking.BookingStatus.ACCEPTED, pageable, caller);
        var response = page.getContent().get(0);

        assertThat(response.carrierCompanyId()).isEqualTo(10L);
        assertThat(response.freightId()).isEqualTo(30L);
        assertThat(response.shipperName()).isEqualTo("Shipper");
        assertThat(response.estimatedPrice()).isNull();
        assertThat(response.commissionFee()).isNull();
        verify(companyContextService).resolveForCompany(10L, caller);
    }

    @Test
    void fleetManagerGetsFinancialFieldsForTheirCompanyBooking() {
        resolveAs(CompanyRole.FLEET_MANAGER);
        var pageable = PageRequest.of(0, 20);
        when(bookingRepository.findAll(
                any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(booking()), pageable, 1));

        var response = service.getBookings(10L, null, pageable, caller)
                .getContent().get(0);

        assertThat(response.estimatedPrice()).isEqualByComparingTo("500.00");
        assertThat(response.commissionFee()).isEqualByComparingTo("25.00");
    }

    @Test
    void detailLookupDoesNotReturnBookingOutsideCompanyScope() {
        resolveAs(CompanyRole.CARRIER_OWNER);
        when(bookingRepository.findOne(any(Specification.class))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getBooking(10L, 999L, caller))
                .isInstanceOf(com.ethioloadai.booking.exception.BookingNotFoundException.class);
    }

    private void resolveAs(CompanyRole role) {
        CompanyMembership membership = new CompanyMembership();
        membership.setRole(role);
        when(companyContextService.resolveForCompany(10L, caller))
                .thenReturn(new CompanyContext(
                        membership, CarrierCompany.builder().id(10L).build()));
    }

    private Booking booking() {
        User driver = User.builder().id(5L).role(User.Role.DRIVER).fullName("Driver").build();
        User shipper = User.builder().id(6L).role(User.Role.SHIPPER).fullName("Shipper").build();
        Freight freight = Freight.builder().id(30L).cargoDescription("Cargo").shipper(shipper).build();
        Vehicle vehicle = Vehicle.builder().id(40L).plateNumber("ABC-123").truckType("Truck").build();
        return Booking.builder()
                .id(50L)
                .carrierCompanyId(10L)
                .cargoRequest(freight)
                .driver(driver)
                .vehicle(vehicle)
                .bookingStatus(Booking.BookingStatus.ACCEPTED)
                .estimatedPrice(new BigDecimal("500.00"))
                .commissionFee(new BigDecimal("25.00"))
                .build();
    }
}
