package com.ethioloadai.trip;

import com.ethioloadai.booking.entity.Booking;
import com.ethioloadai.booking.repository.BookingRepository;
import com.ethioloadai.freight.entity.Freight;
import com.ethioloadai.freight.repository.FreightRepository;
import com.ethioloadai.trip.dto.TripResponse;
import com.ethioloadai.trip.entity.Trip;
import com.ethioloadai.trip.entity.TripStop;
import com.ethioloadai.trip.mapper.TripMapper;
import com.ethioloadai.trip.repository.TripRepository;
import com.ethioloadai.trip.service.TripSecurityService;
import com.ethioloadai.trip.service.TripInvariantService;
import com.ethioloadai.security.jwt.JwtService;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.user.repository.UserRepository;
import com.ethioloadai.vehicle.entity.Vehicle;
import com.ethioloadai.vehicle.repository.VehicleRepository;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.jpa.properties.hibernate.session_factory.statement_inspector=com.ethioloadai.trip.TripPaginationIntegrationTest$SqlStatementCapture"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class TripPaginationIntegrationTest {

    @Autowired private TripRepository tripRepository;
    @Autowired private TripSecurityService tripSecurityService;
    @Autowired private TripInvariantService tripInvariantService;
    @Autowired private UserRepository userRepository;
    @Autowired private FreightRepository freightRepository;
    @Autowired private VehicleRepository vehicleRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private jakarta.persistence.EntityManager entityManager;
    @Autowired private TripMapper tripMapper;
    @Autowired private JwtService jwtService;
    @Autowired private MockMvc mvc;

    @BeforeEach
    void clearSqlEvidence() {
        SqlStatementCapture.SQL.clear();
    }

    @Test
    void databasePagesAreScopedAndLoadStopsInOneBoundedBatch() {
        User admin = user(User.Role.ADMIN);
        User shipperA = user(User.Role.SHIPPER);
        User shipperB = user(User.Role.SHIPPER);
        User driverA = user(User.Role.DRIVER);
        User driverB = user(User.Role.DRIVER);

        Trip oldest = trip(shipperA, driverA, 1);
        Trip middle = trip(shipperA, driverA, 2);
        Trip newest = trip(shipperB, driverB, 3);
        entityManager.flush();
        entityManager.clear();
        SqlStatementCapture.SQL.clear();

        Pageable firstPageRequest = PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "startTime"));
        Page<Trip> firstPage = tripSecurityService.getAuthorizedTrips(admin, firstPageRequest);
        assertThat(firstPage.getContent()).extracting(Trip::getId)
                .containsExactly(newest.getId(), middle.getId());
        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getContent()).allSatisfy(trip ->
                assertThat(org.hibernate.Hibernate.isInitialized(trip.getTripStops())).isTrue());

        List<TripResponse> responses = firstPage.map(tripMapper::toResponse).getContent();
        assertThat(responses).extracting(TripResponse::getStops).allSatisfy(stops ->
                assertThat(stops).hasSize(1));
        assertThat(SqlStatementCapture.SQL).anyMatch(sql ->
                sql.toLowerCase().contains("fetch first") || sql.toLowerCase().contains(" limit "));
        assertSingleBoundedStopFetch();

        SqlStatementCapture.SQL.clear();
        Page<Trip> secondPage = tripSecurityService.getAuthorizedTrips(
                admin, PageRequest.of(1, 2, Sort.by(Sort.Direction.DESC, "startTime")));
        assertThat(secondPage.getContent()).extracting(Trip::getId).containsExactly(oldest.getId());
        assertThat(java.util.stream.Stream.concat(
                firstPage.getContent().stream(), secondPage.getContent().stream())
                .map(Trip::getId).distinct()).hasSize(3);
        assertSingleBoundedStopFetch();

        Page<Trip> driverTrips = tripSecurityService.getAuthorizedTrips(
                driverA, PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "startTime")));
        assertThat(driverTrips.getContent()).extracting(Trip::getId)
                .containsExactlyInAnyOrder(oldest.getId(), middle.getId());

        Page<Trip> shipperTrips = tripSecurityService.getAuthorizedTrips(
                shipperA, PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "startTime")));
        assertThat(shipperTrips.getContent()).extracting(Trip::getId)
                .containsExactlyInAnyOrder(oldest.getId(), middle.getId());

        SqlStatementCapture.SQL.clear();
        Page<Trip> filteredDriverTrips = tripSecurityService.getAuthorizedTripsByStatus(
                driverA, Trip.TripStatus.ONGOING,
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "startTime")));
        assertThat(filteredDriverTrips.getContent()).extracting(Trip::getId)
                .containsExactlyInAnyOrder(oldest.getId(), middle.getId());
        assertThat(filteredDriverTrips.getContent()).allSatisfy(trip ->
                assertThat(org.hibernate.Hibernate.isInitialized(trip.getTripStops())).isTrue());
        assertSingleBoundedStopFetch();
    }

    @Test
    void tripEndpointRetainsDefaultAndMaximumPageSizes() throws Exception {
        User user = user(User.Role.ADMIN);
        String authorization = "Bearer " + jwtService.generateToken(user);

        mvc.perform(get("/trips").header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.content").isArray());

        mvc.perform(get("/trips").queryParam("size", "100")
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));

        mvc.perform(get("/trips").queryParam("size", "150")
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void operationalTripPagesFilterByStatusAndOwnershipWithStableBoundaries() throws Exception {
        User shipper = user(User.Role.SHIPPER);
        User otherShipper = user(User.Role.SHIPPER);
        User driver = user(User.Role.DRIVER);
        User otherDriver = user(User.Role.DRIVER);
        User admin = user(User.Role.ADMIN);
        User fleetOwner = user(User.Role.FLEET_OWNER);
        List<Trip> operational = List.of(
                trip(shipper, driver, 1, Trip.TripStatus.ONGOING),
                trip(shipper, driver, 2, Trip.TripStatus.ARRIVED),
                trip(shipper, driver, 3, Trip.TripStatus.DELIVERY_PENDING_VERIFICATION));
        trip(shipper, driver, 4, Trip.TripStatus.COMPLETED);
        trip(otherShipper, otherDriver, 5, Trip.TripStatus.ONGOING);
        LocalDateTime sameStartTime = LocalDateTime.of(2026, 2, 1, 12, 0);
        operational.forEach(trip -> trip.setStartTime(sameStartTime));
        tripRepository.saveAll(operational);
        entityManager.flush();
        entityManager.clear();

        List<Long> expectedOrder = operational.stream()
                .map(Trip::getId)
                .sorted(java.util.Comparator.reverseOrder())
                .toList();
        String authorization = "Bearer " + jwtService.generateToken(shipper);
        mvc.perform(get("/trips")
                        .queryParam("status", "ONGOING", "ARRIVED", "DELIVERY_PENDING_VERIFICATION")
                        .queryParam("page", "0")
                        .queryParam("size", "2")
                        .queryParam("sortBy", "startTime")
                        .queryParam("sortDir", "desc")
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(expectedOrder.get(0)))
                .andExpect(jsonPath("$.content[1].id").value(expectedOrder.get(1)));
        assertThat(SqlStatementCapture.SQL).anyMatch(sql -> {
            String normalized = sql.toLowerCase();
            return normalized.contains("trip_status in")
                    && normalized.contains("shipper_id")
                    && (normalized.contains("fetch first") || normalized.contains(" limit "));
        });

        mvc.perform(get("/trips")
                        .queryParam("status", "ONGOING", "ARRIVED", "DELIVERY_PENDING_VERIFICATION")
                        .queryParam("page", "1")
                        .queryParam("size", "2")
                        .queryParam("sortBy", "startTime")
                        .queryParam("sortDir", "desc")
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(expectedOrder.get(2)));

        mvc.perform(get("/trips").queryParam("status", "ONGOING")
                        .queryParam("size", "10")
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].tripStatus").value("ONGOING"));

        String driverAuthorization = "Bearer " + jwtService.generateToken(driver);
        mvc.perform(get("/trips")
                        .queryParam("status", "ONGOING", "ARRIVED", "DELIVERY_PENDING_VERIFICATION")
                        .queryParam("size", "10")
                        .header("Authorization", driverAuthorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3));

        mvc.perform(get("/trips")
                        .queryParam("status", "ONGOING", "ARRIVED", "DELIVERY_PENDING_VERIFICATION")
                        .queryParam("size", "10")
                        .header("Authorization", "Bearer " + jwtService.generateToken(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4));

        mvc.perform(get("/trips")
                        .queryParam("status", "ONGOING", "ARRIVED", "DELIVERY_PENDING_VERIFICATION")
                        .queryParam("size", "10")
                        .header("Authorization", "Bearer " + jwtService.generateToken(fleetOwner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void operationalTripPagesRequireAuthenticationAndHandleInvalidBoundsAndEmptyResults() throws Exception {
        User admin = user(User.Role.ADMIN);
        String authorization = "Bearer " + jwtService.generateToken(admin);

        mvc.perform(get("/trips")
                        .queryParam("status", "ONGOING", "ARRIVED", "DELIVERY_PENDING_VERIFICATION"))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/trips").queryParam("page", "-1")
                        .header("Authorization", authorization))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/trips").queryParam("size", "0")
                        .header("Authorization", authorization))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/trips")
                        .queryParam("status", "ONGOING", "ARRIVED", "DELIVERY_PENDING_VERIFICATION")
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0))
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void ongoingDriverPageIsPrincipalScopedBoundedAndIncludesServiceType() throws Exception {
        User shipper = user(User.Role.SHIPPER);
        User driver = user(User.Role.DRIVER);
        User otherDriver = user(User.Role.DRIVER);

        Trip intercity = trip(shipper, driver, 11, Trip.TripStatus.ONGOING);
        Trip intracity = trip(shipper, driver, 12, Trip.TripStatus.ONGOING);
        intracity.getBooking().getCargoRequest().setServiceType(Freight.ServiceType.INTRACITY);
        freightRepository.saveAndFlush(intracity.getBooking().getCargoRequest());
        Trip secondIntercity = trip(shipper, driver, 13, Trip.TripStatus.ONGOING);
        Trip otherDriverTrip = trip(shipper, otherDriver, 14, Trip.TripStatus.ONGOING);
        entityManager.flush();
        entityManager.clear();

        mvc.perform(get("/trips")
                        .queryParam("status", "ONGOING")
                        .queryParam("page", "0")
                        .queryParam("size", "3")
                        .queryParam("sortBy", "id")
                        .queryParam("sortDir", "asc")
                        .header("Authorization", "Bearer " + jwtService.generateToken(driver)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(3))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.last").value(true))
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.content[*].driver.id")
                        .value(org.hamcrest.Matchers.everyItem(
                                org.hamcrest.Matchers.equalTo(driver.getId().intValue()))))
                .andExpect(jsonPath("$.content[*].id")
                        .value(org.hamcrest.Matchers.containsInAnyOrder(
                                intercity.getId().intValue(),
                                intracity.getId().intValue(),
                                secondIntercity.getId().intValue())))
                .andExpect(jsonPath("$.content[*].cargo.serviceType")
                        .value(org.hamcrest.Matchers.containsInAnyOrder(
                                "intercity", "intracity", "intercity")));

        assertThat(otherDriverTrip.getBooking().getDriver().getId()).isNotEqualTo(driver.getId());
    }

    @Test
    void unexpectedFourthOngoingTripIsSignaledByHasNext() throws Exception {
        User shipper = user(User.Role.SHIPPER);
        User driver = user(User.Role.DRIVER);
        for (int index = 1; index <= 4; index++) {
            trip(shipper, driver, index, Trip.TripStatus.ONGOING);
        }
        entityManager.flush();
        entityManager.clear();

        mvc.perform(get("/trips")
                        .queryParam("status", "ONGOING")
                        .queryParam("page", "0")
                        .queryParam("size", "3")
                        .queryParam("sortBy", "id")
                        .queryParam("sortDir", "asc")
                        .header("Authorization", "Bearer " + jwtService.generateToken(driver)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(3))
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.last").value(false))
                .andExpect(jsonPath("$.content.length()").value(3));
    }

    @Test
    void driverCannotExceedThreeConcurrentOngoingTrips() {
        User shipper = user(User.Role.SHIPPER);
        User driver = user(User.Role.DRIVER);
        for (int index = 1; index <= 3; index++) {
            trip(shipper, driver, index, Trip.TripStatus.ONGOING);
        }
        Booking candidate = bookingRepository.saveAndFlush(Booking.builder()
                .cargoRequest(freightRepository.saveAndFlush(Freight.builder()
                        .shipper(shipper)
                        .pickupLocation("Additional pickup")
                        .destination("Additional destination")
                        .materialType("General")
                        .weightTons(BigDecimal.ONE)
                        .budget(BigDecimal.valueOf(1000))
                        .urgencyLevel("NORMAL")
                        .build()))
                .vehicle(vehicleRepository.saveAndFlush(Vehicle.builder()
                        .userId(driver.getId())
                        .truckType("Additional truck")
                        .plateNumber("LIMIT-" + UUID.randomUUID().toString().substring(0, 8))
                        .capacity(10.0)
                        .build()))
                .driver(driver)
                .bookingStatus(Booking.BookingStatus.CONFIRMED)
                .commissionFee(BigDecimal.ZERO)
                .build());

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> tripInvariantService.enforceOneToOneTripBooking(candidate))
                .isInstanceOf(com.ethioloadai.trip.exception.TripValidationException.class);
    }

    @Test
    void vehicleConflictQueryFiltersAndSelectsConflictDeterministically() {
        User shipper = user(User.Role.SHIPPER);
        User driver = user(User.Role.DRIVER);
        Vehicle sharedVehicle = vehicle(driver, "SHARED");
        Vehicle otherVehicle = vehicle(driver, "OTHER");
        Vehicle unrelatedVehicle = vehicle(driver, "UNRELATED");
        Freight freight = freight(shipper, 101);
        Freight unrelatedFreight = freight(shipper, 102);
        Freight pendingFreight = freight(shipper, 103);
        Freight acceptedFreight = freight(shipper, 104);
        Freight ineligibleFreight = freight(shipper, 105);
        Freight currentOnlyFreight = freight(shipper, 106);

        Booking current = booking(freight, sharedVehicle, driver, Booking.BookingStatus.ACCEPTED);
        Booking pending = booking(freight, sharedVehicle, driver, Booking.BookingStatus.PENDING);
        Booking acceptedOlder = booking(freight, sharedVehicle, driver, Booking.BookingStatus.ACCEPTED);
        Booking acceptedTieOne = booking(freight, sharedVehicle, driver, Booking.BookingStatus.ACCEPTED);
        Booking acceptedTieTwo = booking(freight, sharedVehicle, driver, Booking.BookingStatus.ACCEPTED);
        booking(freight, otherVehicle, driver, Booking.BookingStatus.ACCEPTED);
        booking(freight, sharedVehicle, driver, Booking.BookingStatus.CANCELLED);
        booking(freight, sharedVehicle, driver, Booking.BookingStatus.CONFIRMED);
        booking(freight, sharedVehicle, driver, Booking.BookingStatus.COMPLETED);
        booking(freight, sharedVehicle, driver, Booking.BookingStatus.DELIVERED);

        Booking pendingCurrent = booking(
                pendingFreight, sharedVehicle, driver, Booking.BookingStatus.ACCEPTED);
        Booking pendingConflict = booking(
                pendingFreight, sharedVehicle, driver, Booking.BookingStatus.PENDING);
        Booking acceptedCurrent = booking(
                acceptedFreight, sharedVehicle, driver, Booking.BookingStatus.ACCEPTED);
        Booking acceptedConflict = booking(
                acceptedFreight, sharedVehicle, driver, Booking.BookingStatus.ACCEPTED);
        Booking ineligibleCurrent = booking(
                ineligibleFreight, sharedVehicle, driver, Booking.BookingStatus.ACCEPTED);
        booking(ineligibleFreight, sharedVehicle, driver, Booking.BookingStatus.CANCELLED);
        booking(ineligibleFreight, sharedVehicle, driver, Booking.BookingStatus.CONFIRMED);
        booking(ineligibleFreight, sharedVehicle, driver, Booking.BookingStatus.COMPLETED);
        booking(ineligibleFreight, sharedVehicle, driver, Booking.BookingStatus.DELIVERED);
        Booking currentOnly = booking(
                currentOnlyFreight, sharedVehicle, driver, Booking.BookingStatus.ACCEPTED);
        entityManager.flush();

        setBookingUpdatedAt(current, LocalDateTime.of(2030, 1, 1, 0, 0));
        setBookingUpdatedAt(pending, LocalDateTime.of(2029, 1, 1, 0, 0));
        setBookingUpdatedAt(acceptedOlder, LocalDateTime.of(2026, 1, 1, 0, 0));
        LocalDateTime tiedTimestamp = LocalDateTime.of(2026, 2, 1, 0, 0);
        setBookingUpdatedAt(acceptedTieOne, tiedTimestamp);
        setBookingUpdatedAt(acceptedTieTwo, tiedTimestamp);
        setBookingUpdatedAt(pendingConflict, LocalDateTime.of(2026, 3, 1, 0, 0));
        entityManager.clear();
        SqlStatementCapture.SQL.clear();

        List<Booking> conflict = findVehicleConflict(freight, current, sharedVehicle);
        assertThat(conflict).hasSize(1);
        assertThat(conflict.getFirst().getId()).isEqualTo(acceptedTieTwo.getId());
        assertThat(SqlStatementCapture.SQL).anySatisfy(sql -> {
            String normalizedSql = sql.toLowerCase();
            assertThat(normalizedSql).contains("bookings");
            assertThat(normalizedSql).contains("fetch first");
        });

        assertThat(findVehicleConflict(pendingFreight, pendingCurrent, sharedVehicle))
                .extracting(Booking::getId)
                .containsExactly(pendingConflict.getId());
        assertThat(findVehicleConflict(acceptedFreight, acceptedCurrent, sharedVehicle))
                .extracting(Booking::getId)
                .containsExactly(acceptedConflict.getId());
        assertThat(findVehicleConflict(ineligibleFreight, ineligibleCurrent, sharedVehicle))
                .isEmpty();
        assertThat(findVehicleConflict(currentOnlyFreight, currentOnly, sharedVehicle))
                .isEmpty();
        assertThat(findVehicleConflict(unrelatedFreight, current, sharedVehicle))
                .isEmpty();
        assertThat(findVehicleConflict(freight, current, unrelatedVehicle))
                .isEmpty();

        assertThat(findVehicleConflict(freight, current, sharedVehicle))
                .extracting(Booking::getId)
                .doesNotContain(current.getId());
    }

    private User user(User.Role role) {
        String suffix = UUID.randomUUID().toString();
        return userRepository.saveAndFlush(User.builder()
                .fullName("Test " + role)
                .phone("+" + suffix.substring(0, 12))
                .password("test-password")
                .role(role)
                .verificationStatus(true)
                .isActive(true)
                .build());
    }

    private Vehicle vehicle(User driver, String prefix) {
        return vehicleRepository.saveAndFlush(Vehicle.builder()
                .userId(driver.getId())
                .truckType("Test truck")
                .plateNumber(prefix + "-" + UUID.randomUUID().toString().substring(0, 8))
                .capacity(10.0)
                .build());
    }

    private Freight freight(User shipper, int sequence) {
        return freightRepository.saveAndFlush(Freight.builder()
                .shipper(shipper)
                .pickupLocation("Pickup " + sequence)
                .destination("Destination " + sequence)
                .materialType("General")
                .weightTons(BigDecimal.ONE)
                .budget(BigDecimal.valueOf(1000))
                .urgencyLevel("NORMAL")
                .build());
    }

    private Booking booking(Freight freight, Vehicle vehicle, User driver, Booking.BookingStatus status) {
        return bookingRepository.saveAndFlush(Booking.builder()
                .cargoRequest(freight)
                .vehicle(vehicle)
                .driver(driver)
                .bookingStatus(status)
                .commissionFee(BigDecimal.ZERO)
                .build());
    }

    private void setBookingUpdatedAt(Booking booking, LocalDateTime updatedAt) {
        entityManager.createNativeQuery(
                        "UPDATE bookings SET updated_at = :updatedAt WHERE id = :bookingId")
                .setParameter("updatedAt", updatedAt)
                .setParameter("bookingId", booking.getId())
                .executeUpdate();
    }

    private List<Booking> findVehicleConflict(Freight freight, Booking currentBooking, Vehicle vehicle) {
        return bookingRepository.findVehicleConflict(
                freight.getId(),
                currentBooking.getId(),
                vehicle.getId(),
                List.of(Booking.BookingStatus.ACCEPTED, Booking.BookingStatus.PENDING),
                Booking.BookingStatus.ACCEPTED,
                PageRequest.of(0, 1));
    }

    private Trip trip(User shipper, User driver, int sequence) {
        return trip(shipper, driver, sequence, Trip.TripStatus.ONGOING);
    }

    private Trip trip(User shipper, User driver, int sequence, Trip.TripStatus status) {
        Vehicle vehicle = vehicleRepository.saveAndFlush(Vehicle.builder()
                .userId(driver.getId())
                .truckType("Test truck")
                .plateNumber("TEST-" + UUID.randomUUID().toString().substring(0, 8))
                .capacity(10.0)
                .build());
        Freight freight = freightRepository.saveAndFlush(Freight.builder()
                .shipper(shipper)
                .pickupLocation("Pickup " + sequence)
                .destination("Destination " + sequence)
                .materialType("General")
                .weightTons(BigDecimal.ONE)
                .budget(BigDecimal.valueOf(1000))
                .urgencyLevel("NORMAL")
                .build());
        Booking booking = bookingRepository.saveAndFlush(Booking.builder()
                .cargoRequest(freight)
                .vehicle(vehicle)
                .driver(driver)
                .bookingStatus(Booking.BookingStatus.CONFIRMED)
                .commissionFee(BigDecimal.ZERO)
                .build());
        Trip trip = Trip.builder()
                .booking(booking)
                .driver(driver)
                .vehicle(vehicle)
                .startLocation("Origin " + sequence)
                .destination("Destination " + sequence)
                .startTime(LocalDateTime.of(2026, 1, sequence, 12, 0))
                .tripStatus(status)
                .build();
        TripStop stop = TripStop.builder()
                .trip(trip)
                .stopOrder(1)
                .locationName("Stop " + sequence)
                .agreedPrice(BigDecimal.ZERO)
                .build();
        trip.setTripStops(new ArrayList<>(List.of(stop)));
        return tripRepository.saveAndFlush(trip);
    }

    private void assertSingleBoundedStopFetch() {
        assertThat(SqlStatementCapture.SQL.stream()
                .filter(sql -> sql.toLowerCase().contains("trip_stops")))
                .singleElement()
                .satisfies(sql -> assertThat(sql.toLowerCase()).contains(" in ("));
    }

    public static class SqlStatementCapture implements StatementInspector {
        static final List<String> SQL = new CopyOnWriteArrayList<>();

        @Override
        public String inspect(String sql) {
            if (sql.toLowerCase().startsWith("select")) {
                SQL.add(sql);
            }
            return sql;
        }
    }
}
