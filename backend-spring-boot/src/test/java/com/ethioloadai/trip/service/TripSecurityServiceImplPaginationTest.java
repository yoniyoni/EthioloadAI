package com.ethioloadai.trip.service;

import com.ethioloadai.trip.entity.Trip;
import com.ethioloadai.trip.repository.TripRepository;
import com.ethioloadai.trip.repository.TripStopRepository;
import com.ethioloadai.carrier.team.service.FleetOwnerHistoryScopeService;
import com.ethioloadai.booking.entity.Booking;
import com.ethioloadai.freight.entity.Freight;
import com.ethioloadai.freight.service.FreightVisibilityProperties;
import com.ethioloadai.user.entity.User;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TripSecurityServiceImplPaginationTest {

    @Mock private TripRepository tripRepository;
    @Mock private TripStopRepository tripStopRepository;
    @Mock private FleetOwnerHistoryScopeService fleetOwnerHistoryScope;
    @Mock private FreightVisibilityProperties visibilityProperties;

    private TripSecurityServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TripSecurityServiceImpl(
                tripRepository, tripStopRepository, fleetOwnerHistoryScope, visibilityProperties);
    }

    @Test
    void fleetOwnerDefaultTripsRetainPageableAndSerializeAsAnEmptyPage() throws Exception {
        Pageable pageable = requestedPageable();
        when(fleetOwnerHistoryScope.getApprovedCompanyIds(fleetOwner())).thenReturn(List.of());
        when(tripRepository.findAll(org.mockito.ArgumentMatchers.<Specification<Trip>>any(),
                org.mockito.ArgumentMatchers.eq(pageable))).thenReturn(Page.empty(pageable));

        Page<Trip> result = service.getAuthorizedTrips(fleetOwner(), pageable);

        assertEmptyPagedResultAndSerializes(result, pageable);
        verify(tripRepository).findAll(org.mockito.ArgumentMatchers.<Specification<Trip>>any(),
                org.mockito.ArgumentMatchers.eq(pageable));
        verifyNoInteractions(tripStopRepository);
    }

    @Test
    void fleetOwnerStatusFilteredTripsRetainPageableAndSerializeAsAnEmptyPage() throws Exception {
        Pageable pageable = requestedPageable();
        when(fleetOwnerHistoryScope.getApprovedCompanyIds(fleetOwner())).thenReturn(List.of());
        when(tripRepository.findAll(org.mockito.ArgumentMatchers.<Specification<Trip>>any(),
                org.mockito.ArgumentMatchers.eq(pageable))).thenReturn(Page.empty(pageable));

        Page<Trip> result = service.getAuthorizedTripsByStatus(
                fleetOwner(), Trip.TripStatus.ONGOING, pageable);

        assertEmptyPagedResultAndSerializes(result, pageable);
        verify(tripRepository).findAll(org.mockito.ArgumentMatchers.<Specification<Trip>>any(),
                org.mockito.ArgumentMatchers.eq(pageable));
        verifyNoInteractions(tripStopRepository);
    }

    @Test
    void fleetOwnerDateRangeTripsRetainPageableAndSerializeAsAnEmptyPage() throws Exception {
        Pageable pageable = requestedPageable();
        when(fleetOwnerHistoryScope.getApprovedCompanyIds(fleetOwner())).thenReturn(List.of());
        when(tripRepository.findAll(org.mockito.ArgumentMatchers.<Specification<Trip>>any(),
                org.mockito.ArgumentMatchers.eq(pageable))).thenReturn(Page.empty(pageable));

        Page<Trip> result = service.getAuthorizedTripsByDateRange(
                fleetOwner(), LocalDateTime.parse("2026-01-01T00:00:00"),
                LocalDateTime.parse("2026-12-31T23:59:59"), pageable);

        assertEmptyPagedResultAndSerializes(result, pageable);
        verify(tripRepository).findAll(org.mockito.ArgumentMatchers.<Specification<Trip>>any(),
                org.mockito.ArgumentMatchers.eq(pageable));
        verifyNoInteractions(tripStopRepository);
    }

    @Test
    void fleetOwnerWithApprovedMembershipUsesCompanyScopedDefaultAndStatusQueries() {
        User owner = fleetOwner();
        Pageable pageable = requestedPageable();
        List<Long> companyIds = List.of(71L);
        Page<Trip> expected = Page.empty(pageable);
        when(fleetOwnerHistoryScope.getApprovedCompanyIds(owner)).thenReturn(companyIds);
        when(tripRepository.findAll(org.mockito.ArgumentMatchers.<Specification<Trip>>any(),
                org.mockito.ArgumentMatchers.eq(pageable))).thenReturn(expected);

        assertThat(service.getAuthorizedTrips(owner, pageable)).isSameAs(expected);
        assertThat(service.getAuthorizedTripsByStatus(owner, Trip.TripStatus.ONGOING, pageable))
                .isSameAs(expected);
    }

    @Test
    void fleetOwnerWithApprovedMembershipUsesCompanyScopedMultiStatusAndDateQueries() {
        User owner = fleetOwner();
        Pageable pageable = requestedPageable();
        List<Long> companyIds = List.of(71L);
        List<Trip.TripStatus> statuses = List.of(Trip.TripStatus.ONGOING, Trip.TripStatus.ARRIVED);
        LocalDateTime start = LocalDateTime.parse("2026-01-01T00:00:00");
        LocalDateTime end = LocalDateTime.parse("2026-12-31T23:59:59");
        Page<Trip> expected = Page.empty(pageable);
        when(fleetOwnerHistoryScope.getApprovedCompanyIds(owner)).thenReturn(companyIds);
        when(tripRepository.findAll(org.mockito.ArgumentMatchers.<Specification<Trip>>any(),
                org.mockito.ArgumentMatchers.eq(pageable))).thenReturn(expected);

        assertThat(service.getAuthorizedTripsByStatuses(owner, statuses, pageable)).isSameAs(expected);
        assertThat(service.getAuthorizedTripsByDateRange(owner, start, end, pageable)).isSameAs(expected);
    }

    @Test
    void driverTripPaginationStillUsesAuthorizedRepositoryQuery() {
        User driver = User.builder().id(42L).role(User.Role.DRIVER).build();
        Pageable pageable = requestedPageable();
        Page<Trip> expected = new PageImpl<>(List.of(), pageable, 0);
        when(visibilityProperties.getDriverHistoryDays()).thenReturn(30L);
        when(tripRepository.findAll(org.mockito.ArgumentMatchers.<Specification<Trip>>any(),
                org.mockito.ArgumentMatchers.eq(pageable))).thenReturn(expected);

        Page<Trip> result = service.getAuthorizedTrips(driver, pageable);

        assertThat(result).isSameAs(expected);
        assertThat(result.getPageable()).isEqualTo(pageable);
        verify(tripRepository).findAll(org.mockito.ArgumentMatchers.<Specification<Trip>>any(),
                org.mockito.ArgumentMatchers.eq(pageable));
        verifyNoInteractions(tripStopRepository);
    }

    @Test
    void expiredCompletedDriverTripIsDeniedByIdentifierAndRemovedFromListContent() {
        User driver = User.builder().id(42L).role(User.Role.DRIVER).build();
        Pageable pageable = PageRequest.of(0, 17, Sort.by(Sort.Direction.DESC, "startTime"));
        Trip expired = completedTrip(7L, driver, LocalDateTime.now().minusDays(31));
        Trip active = Trip.builder()
                .id(8L)
                .tripStatus(Trip.TripStatus.ONGOING)
                .booking(Booking.builder()
                        .driver(driver)
                        .cargoRequest(Freight.builder().status(Freight.FreightStatus.IN_TRANSIT).build())
                        .build())
                .build();
        when(visibilityProperties.getDriverHistoryDays()).thenReturn(30L);
        when(tripRepository.findByIdAndDriverId(7L, driver.getId()))
                .thenReturn(java.util.Optional.of(expired));
        when(tripRepository.findAll(org.mockito.ArgumentMatchers.<Specification<Trip>>any(),
                org.mockito.ArgumentMatchers.eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(active), pageable, 1));

        assertThat(service.getAuthorizedTrip(7L, driver)).isEmpty();
        Page<Trip> result = service.getAuthorizedTrips(driver, pageable);
        assertThat(result.getContent()).containsExactly(active);
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void expiredCompletedTripIsDeniedToCarrierAfterConfiguredWindow() {
        User owner = fleetOwner();
        Trip expired = completedTrip(7L, User.builder().id(42L).role(User.Role.DRIVER).build(),
                LocalDateTime.now().minusDays(91));
        when(fleetOwnerHistoryScope.getApprovedCompanyIds(owner)).thenReturn(List.of(71L));
        when(tripRepository.findByIdAndCarrierCompanyIds(7L, List.of(71L)))
                .thenReturn(java.util.Optional.of(expired));
        when(visibilityProperties.getCarrierHistoryDays()).thenReturn(90L);

        assertThat(service.getAuthorizedTrip(7L, owner)).isEmpty();
    }

    @Test
    void completedBookingExpiresTripDespiteStaleFreightAndTripStatuses() {
        User driver = User.builder().id(42L).role(User.Role.DRIVER).build();
        Trip trip = Trip.builder()
                .id(9L)
                .tripStatus(Trip.TripStatus.ONGOING)
                .booking(Booking.builder()
                        .driver(driver)
                        .bookingStatus(Booking.BookingStatus.COMPLETED)
                        .updatedAt(LocalDateTime.now().minusDays(31))
                        .cargoRequest(Freight.builder()
                                .status(Freight.FreightStatus.IN_TRANSIT)
                                .build())
                        .build())
                .build();
        when(visibilityProperties.getDriverHistoryDays()).thenReturn(30L);
        when(tripRepository.findByIdAndDriverId(9L, driver.getId()))
                .thenReturn(java.util.Optional.of(trip));

        assertThat(service.getAuthorizedTrip(9L, driver)).isEmpty();
    }

    @Test
    void zeroDayDriverHistoryDeniesRecentlyCompletedTrip() {
        User driver = User.builder().id(42L).role(User.Role.DRIVER).build();
        Trip trip = completedTrip(10L, driver, LocalDateTime.now());
        when(visibilityProperties.getDriverHistoryDays()).thenReturn(0L);
        when(tripRepository.findByIdAndDriverId(10L, driver.getId()))
                .thenReturn(java.util.Optional.of(trip));

        assertThat(service.getAuthorizedTrip(10L, driver)).isEmpty();
    }

    @Test
    void completedTripWithNullEndTimeIsDeniedToDriverEvenWhenFreightStatusIsStale() {
        User driver = User.builder().id(42L).role(User.Role.DRIVER).build();
        Trip trip = completedTrip(11L, driver, null);
        trip.getBooking().getCargoRequest().setStatus(Freight.FreightStatus.IN_TRANSIT);
        when(tripRepository.findByIdAndDriverId(11L, driver.getId()))
                .thenReturn(java.util.Optional.of(trip));

        assertThat(service.getAuthorizedTrip(11L, driver)).isEmpty();
    }

    private static Trip completedTrip(Long id, User driver, LocalDateTime endTime) {
        return Trip.builder()
                .id(id)
                .tripStatus(Trip.TripStatus.COMPLETED)
                .endTime(endTime)
                .booking(Booking.builder()
                        .driver(driver)
                        .cargoRequest(Freight.builder().status(Freight.FreightStatus.COMPLETED).build())
                        .build())
                .build();
    }

    private static void assertEmptyPagedResultAndSerializes(Page<Trip> result, Pageable pageable)
            throws Exception {
        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
        assertThat(result.getNumber()).isEqualTo(pageable.getPageNumber());
        assertThat(result.getSize()).isEqualTo(pageable.getPageSize());
        assertThat(result.getPageable()).isEqualTo(pageable);

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(result));
        assertThat(json.path("content").isArray()).isTrue();
        assertThat(json.path("content")).isEmpty();
        assertThat(json.path("number").asInt()).isEqualTo(pageable.getPageNumber());
        assertThat(json.path("size").asInt()).isEqualTo(pageable.getPageSize());
        assertThat(json.path("totalElements").asLong()).isZero();
        assertThat(json.path("pageable").path("paged").asBoolean()).isTrue();
        assertThat(json.path("pageable").path("offset").asLong()).isEqualTo(pageable.getOffset());
    }

    private static Pageable requestedPageable() {
        return PageRequest.of(2, 17, Sort.by(Sort.Direction.DESC, "startTime"));
    }

    private static User fleetOwner() {
        return User.builder().id(18L).role(User.Role.FLEET_OWNER).build();
    }
}
