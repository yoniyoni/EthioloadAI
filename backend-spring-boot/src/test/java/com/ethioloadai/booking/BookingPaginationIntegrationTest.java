package com.ethioloadai.booking;

import com.ethioloadai.booking.entity.Booking;
import com.ethioloadai.booking.repository.BookingRepository;
import com.ethioloadai.freight.entity.Freight;
import com.ethioloadai.freight.repository.FreightRepository;
import com.ethioloadai.security.jwt.JwtService;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.user.repository.UserRepository;
import com.ethioloadai.vehicle.entity.Vehicle;
import com.ethioloadai.vehicle.repository.VehicleRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.jpa.properties.hibernate.session_factory.statement_inspector=com.ethioloadai.booking.BookingPaginationIntegrationTest$SqlCapture"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class BookingPaginationIntegrationTest {
    @Autowired private BookingRepository bookingRepository;
    @Autowired private FreightRepository freightRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private VehicleRepository vehicleRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private JwtService jwtService;
    @Autowired private MockMvc mvc;

    @BeforeEach
    void clearSqlCapture() {
        SqlCapture.SQL.clear();
    }

    @Test
    void highestIdNonCancelledBookingQueryIsBoundedAndExcludesCancelledHistory() {
        User shipper = user(User.Role.SHIPPER);
        User driver = user(User.Role.DRIVER);
        Vehicle vehicle = vehicle(driver);
        Freight freight = freight(shipper);
        Booking olderEligible = booking(freight, vehicle, driver);
        Booking newestEligible = booking(freight, vehicle, driver);
        Booking newestCancelled = booking(freight, vehicle, driver);
        newestEligible.setBookingStatus(Booking.BookingStatus.DELIVERED);
        newestCancelled.setBookingStatus(Booking.BookingStatus.CANCELLED);
        entityManager.flush();
        entityManager.clear();

        Optional<Booking> selected = bookingRepository
                .findFirstByCargoRequest_IdAndBookingStatusNotOrderByIdDesc(
                        freight.getId(), Booking.BookingStatus.CANCELLED);

        assertThat(selected).isPresent();
        assertThat(selected.orElseThrow().getId()).isEqualTo(newestEligible.getId());

        bookingRepository.findById(olderEligible.getId()).orElseThrow()
                .setBookingStatus(Booking.BookingStatus.CANCELLED);
        bookingRepository.findById(newestEligible.getId()).orElseThrow()
                .setBookingStatus(Booking.BookingStatus.CANCELLED);
        entityManager.flush();
        entityManager.clear();

        assertThat(bookingRepository.findFirstByCargoRequest_IdAndBookingStatusNotOrderByIdDesc(
                freight.getId(), Booking.BookingStatus.CANCELLED)).isEmpty();
    }

    @Test
    void shipperAndDriverPagesAreIsolatedAndIgnoreCallerSuppliedOwnerIds() throws Exception {
        User shipper = user(User.Role.SHIPPER);
        User otherShipper = user(User.Role.SHIPPER);
        User driver = user(User.Role.DRIVER);
        User otherDriver = user(User.Role.DRIVER);
        Vehicle vehicle = vehicle(driver);
        Vehicle otherVehicle = vehicle(otherDriver);
        Booking ownShipperBooking = booking(freight(shipper), vehicle, driver);
        booking(freight(otherShipper), vehicle, otherDriver);
        booking(freight(otherShipper), otherVehicle, otherDriver);
        entityManager.flush();

        mvc.perform(get("/bookings/page").header("Authorization", bearer(shipper))
                        .queryParam("shipperId", otherShipper.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(ownShipperBooking.getId()));

        Booking ownDriverBooking = booking(freight(otherShipper), otherVehicle, otherDriver);
        entityManager.flush();
        mvc.perform(get("/bookings/page").header("Authorization", bearer(driver))
                        .queryParam("driverId", otherDriver.getId().toString())
                        .queryParam("shipperId", otherShipper.getId().toString()))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/bookings/page").header("Authorization", bearer(otherDriver))
                        .queryParam("driverId", driver.getId().toString()))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/bookings/page").header("Authorization", bearer(otherDriver)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.content[*].id")
                        .value(org.hamcrest.Matchers.hasItem(ownDriverBooking.getId().intValue())));
    }

    @Test
    void adminCanPageAllBookingsAndFilterOneDriversBookingsWithExactCount() throws Exception {
        User admin = user(User.Role.ADMIN);
        User driver = user(User.Role.DRIVER);
        User otherDriver = user(User.Role.DRIVER);
        Vehicle vehicle = vehicle(driver);
        Vehicle otherVehicle = vehicle(otherDriver);
        Booking first = booking(freight(user(User.Role.SHIPPER)), vehicle, driver);
        Booking second = booking(freight(user(User.Role.SHIPPER)), vehicle, driver);
        booking(freight(user(User.Role.SHIPPER)), otherVehicle, otherDriver);
        entityManager.flush();

        mvc.perform(get("/bookings/page").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.totalBookings").doesNotExist());
        mvc.perform(get("/bookings/page").header("Authorization", bearer(admin))
                        .queryParam("driverId", driver.getId().toString()).queryParam("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].driverId").value(driver.getId()))
                .andExpect(jsonPath("$.totalBookings").value(2))
                .andExpect(jsonPath("$.hasNext").value(true));
        mvc.perform(get("/bookings/page").header("Authorization", bearer(admin))
                        .queryParam("driverId", "-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalBookings").value(0))
                .andExpect(jsonPath("$.hasNext").value(false));
        assertThat(first.getId()).isNotEqualTo(second.getId());
    }

    @Test
    void fleetOwnerPagePreservesLegacySelfAsDriverPredicate() throws Exception {
        User fleetOwner = user(User.Role.FLEET_OWNER);
        User fleetDriver = user(User.Role.DRIVER);
        Vehicle ownerVehicle = vehicle(fleetOwner);
        Vehicle driverVehicle = vehicle(fleetDriver);
        Booking directlyAssigned = booking(freight(user(User.Role.SHIPPER)), ownerVehicle, fleetOwner);
        booking(freight(user(User.Role.SHIPPER)), driverVehicle, fleetDriver);
        entityManager.flush();

        mvc.perform(get("/bookings/page").header("Authorization", bearer(fleetOwner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(directlyAssigned.getId()));
    }

    @Test
    void pageBoundsOrderingAndFinalEmptyPageAreDeterministic() throws Exception {
        User admin = user(User.Role.ADMIN);
        User driver = user(User.Role.DRIVER);
        Vehicle vehicle = vehicle(driver);
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 105; i++) {
            ids.add(booking(freight(user(User.Role.SHIPPER)), vehicle, driver).getId());
        }
        entityManager.flush();
        entityManager.createNativeQuery("UPDATE bookings SET created_at = :createdAt")
                .setParameter("createdAt", LocalDateTime.of(2026, 1, 1, 12, 0))
                .executeUpdate();
        entityManager.clear();
        String token = bearer(admin);

        mvc.perform(get("/bookings/page").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.content.length()").value(20))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.content[0].id").value(ids.get(104)));
        mvc.perform(get("/bookings/page").header("Authorization", token)
                        .queryParam("size", "10000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100))
                .andExpect(jsonPath("$.content.length()").value(100));
        mvc.perform(get("/bookings/page").header("Authorization", token)
                        .queryParam("page", "1").queryParam("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(ids.get(84)));
        mvc.perform(get("/bookings/page").header("Authorization", token)
                        .queryParam("page", "99").queryParam("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.hasNext").value(false));
        mvc.perform(get("/bookings/page").header("Authorization", token).queryParam("size", "0"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/bookings/page").header("Authorization", token).queryParam("size", "-1"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/bookings/page").header("Authorization", token).queryParam("page", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unauthenticatedRequestsAreRejectedAndMappingHydratesAssociationsInOneBoundedQuery() throws Exception {
        mvc.perform(get("/bookings/page")).andExpect(status().isUnauthorized());

        User admin = user(User.Role.ADMIN);
        User driver = user(User.Role.DRIVER);
        Vehicle vehicle = vehicle(driver);
        Freight freight = freight(user(User.Role.SHIPPER));
        booking(freight, vehicle, driver);
        booking(freight(user(User.Role.SHIPPER)), vehicle, driver);
        entityManager.flush();
        entityManager.clear();
        SqlCapture.SQL.clear();

        mvc.perform(get("/bookings/page").header("Authorization", bearer(admin)).queryParam("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].freightDescription").value(freight.getCargoDescription()))
                .andExpect(jsonPath("$.content[0].vehiclePlateNumber").value(vehicle.getPlateNumber()))
                .andExpect(jsonPath("$.content[0].driverName").value(driver.getFullName()));

        List<String> bookingQueries = SqlCapture.SQL.stream()
                .filter(sql -> sql.toLowerCase().contains(" from bookings"))
                .toList();
        assertThat(bookingQueries).hasSize(2);
        assertThat(bookingQueries.stream().filter(sql -> sql.toLowerCase().contains("fetch first")).count())
                .isEqualTo(1);
        assertThat(bookingQueries.stream().filter(sql -> sql.toLowerCase().contains(" left join")).count())
                .isEqualTo(1);
    }

    private User user(User.Role role) {
        String suffix = UUID.randomUUID().toString();
        return userRepository.saveAndFlush(User.builder()
                .fullName("Booking page " + role)
                .phone("+" + suffix.substring(0, 12))
                .password("test-password")
                .role(role)
                .verificationStatus(true)
                .isActive(true)
                .build());
    }

    private Vehicle vehicle(User driver) {
        return vehicleRepository.saveAndFlush(Vehicle.builder()
                .userId(driver.getId())
                .truckType("Booking test truck")
                .plateNumber("BOOK-" + UUID.randomUUID().toString().substring(0, 8))
                .capacity(10.0)
                .build());
    }

    private Freight freight(User shipper) {
        return freightRepository.saveAndFlush(Freight.builder()
                .shipper(shipper)
                .pickupLocation("Booking pickup")
                .destination("Booking destination")
                .materialType("General")
                .cargoDescription("Booking freight description")
                .weightTons(BigDecimal.ONE)
                .budget(BigDecimal.valueOf(1000))
                .urgencyLevel("normal")
                .status(Freight.FreightStatus.MATCHED)
                .build());
    }

    private Booking booking(Freight freight, Vehicle vehicle, User driver) {
        return bookingRepository.saveAndFlush(Booking.builder()
                .cargoRequest(freight)
                .vehicle(vehicle)
                .driver(driver)
                .bookingStatus(Booking.BookingStatus.ACCEPTED)
                .estimatedPrice(BigDecimal.valueOf(1000))
                .commissionFee(BigDecimal.ZERO)
                .build());
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generateToken(user);
    }

    public static class SqlCapture implements StatementInspector {
        static final List<String> SQL = new CopyOnWriteArrayList<>();

        @Override
        public String inspect(String sql) {
            if (sql.stripLeading().toLowerCase().startsWith("select")) {
                SQL.add(sql);
            }
            return sql;
        }
    }
}
