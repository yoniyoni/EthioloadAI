package com.ethioloadai.history;

import com.ethioloadai.booking.entity.Booking;
import com.ethioloadai.booking.repository.BookingRepository;
import com.ethioloadai.freight.entity.Freight;
import com.ethioloadai.freight.repository.FreightRepository;
import com.ethioloadai.history.repository.HistoryRepository;
import com.ethioloadai.payment.entity.Payment;
import com.ethioloadai.payment.repository.PaymentRepository;
import com.ethioloadai.security.jwt.JwtService;
import com.ethioloadai.trip.entity.Trip;
import com.ethioloadai.trip.repository.TripRepository;
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
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.jpa.properties.hibernate.session_factory.statement_inspector=com.ethioloadai.history.HistoryPaginationIntegrationTest$SqlCapture"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class HistoryPaginationIntegrationTest {
    @Autowired private FreightRepository freightRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private TripRepository tripRepository;
    @Autowired private PaymentRepository paymentRepository;
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
    void historyIsOwnerScopedAndRequiresAuthenticatedShipper() throws Exception {
        User shipper = user(User.Role.SHIPPER);
        User otherShipper = user(User.Role.SHIPPER);
        User driver = user(User.Role.DRIVER);
        Freight owned = freight(shipper);
        Freight other = freight(otherShipper);
        entityManager.flush();

        mvc.perform(get("/history/shipper/freight")
                        .header("Authorization", bearer(shipper))
                        .queryParam("shipperId", otherShipper.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].freight.id").value(owned.getId()))
                .andExpect(jsonPath("$.content[0].freight.id").value(org.hamcrest.Matchers.not(other.getId())));

        mvc.perform(get("/history/shipper/freight"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/history/shipper/freight")
                        .header("Authorization", bearer(driver)))
                .andExpect(status().isForbidden());
    }

    @Test
    void representativeBookingPrefersActiveAndItsTripAndPayment() throws Exception {
        User shipper = user(User.Role.SHIPPER);
        User driver = user(User.Role.DRIVER);
        Vehicle vehicle = vehicle(driver);
        Freight freight = freight(shipper);
        Booking accepted = booking(freight, vehicle, driver, Booking.BookingStatus.ACCEPTED);
        Booking pending = booking(freight, vehicle, driver, Booking.BookingStatus.PENDING);
        Booking cancelled = booking(freight, vehicle, driver, Booking.BookingStatus.CANCELLED);
        entityManager.flush();

        LocalDateTime sharedTimestamp = LocalDateTime.of(2026, 2, 1, 10, 0);
        entityManager.createNativeQuery("UPDATE bookings SET updated_at = :updatedAt WHERE id IN (:firstId, :secondId)")
                .setParameter("updatedAt", sharedTimestamp)
                .setParameter("firstId", accepted.getId())
                .setParameter("secondId", pending.getId())
                .executeUpdate();
        entityManager.createNativeQuery("UPDATE bookings SET updated_at = :updatedAt WHERE id = :id")
                .setParameter("updatedAt", sharedTimestamp.plusDays(1))
                .setParameter("id", cancelled.getId())
                .executeUpdate();

        Trip acceptedTrip = trip(accepted, vehicle, driver);
        Trip pendingTrip = trip(pending, vehicle, driver);
        tripRepository.saveAndFlush(acceptedTrip);
        tripRepository.saveAndFlush(pendingTrip);
        Payment acceptedPayment = payment(accepted, shipper);
        Payment pendingPayment = payment(pending, shipper);
        paymentRepository.saveAndFlush(acceptedPayment);
        paymentRepository.saveAndFlush(pendingPayment);
        entityManager.flush();
        entityManager.clear();

        mvc.perform(get("/history/shipper/freight")
                        .header("Authorization", bearer(shipper)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].booking.id").value(pending.getId()))
                .andExpect(jsonPath("$.content[0].booking.bookingStatus").value("pending"))
                .andExpect(jsonPath("$.content[0].trip.id").value(pendingTrip.getId()))
                .andExpect(jsonPath("$.content[0].trip.driver.fullName").value(driver.getFullName()))
                .andExpect(jsonPath("$.content[0].payment.id").value(pendingPayment.getId()))
                .andExpect(jsonPath("$.content[0].payment.paymentStatus").value("pending"));
    }

    @Test
    void nonCancelledFallbackPrecedesCancelledAndAllCancelledUsesLatestDeterministically() throws Exception {
        User shipper = user(User.Role.SHIPPER);
        User driver = user(User.Role.DRIVER);
        Vehicle vehicle = vehicle(driver);
        Freight freightWithFallback = freight(shipper);
        Booking nonCancelled = booking(freightWithFallback, vehicle, driver, Booking.BookingStatus.COMPLETED);
        Booking cancelled = booking(freightWithFallback, vehicle, driver, Booking.BookingStatus.CANCELLED);
        Freight freightWithAllCancelled = freight(shipper);
        Booking olderCancelled = booking(
                freightWithAllCancelled, vehicle, driver, Booking.BookingStatus.CANCELLED);
        Booking latestCancelled = booking(
                freightWithAllCancelled, vehicle, driver, Booking.BookingStatus.CANCELLED);
        Freight freightWithNoBooking = freight(shipper);
        Freight freightWithBookingOnly = freight(shipper);
        Booking bookingOnly = booking(
                freightWithBookingOnly, vehicle, driver, Booking.BookingStatus.DELIVERED);
        Freight freightWithTripOnly = freight(shipper);
        Booking tripOnlyBooking = booking(
                freightWithTripOnly, vehicle, driver, Booking.BookingStatus.ACCEPTED);
        Trip trip = tripRepository.saveAndFlush(trip(tripOnlyBooking, vehicle, driver));
        entityManager.flush();

        LocalDateTime sharedTimestamp = LocalDateTime.of(2026, 3, 1, 9, 0);
        entityManager.createNativeQuery(
                        "UPDATE bookings SET updated_at = :updatedAt WHERE id IN (:firstId, :secondId, :thirdId, :fourthId)")
                .setParameter("updatedAt", sharedTimestamp)
                .setParameter("firstId", nonCancelled.getId())
                .setParameter("secondId", cancelled.getId())
                .setParameter("thirdId", olderCancelled.getId())
                .setParameter("fourthId", latestCancelled.getId())
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();

        mvc.perform(get("/history/shipper/freight").queryParam("size", "10")
                        .header("Authorization", bearer(shipper)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.freight.id == " + freightWithFallback.getId() + ")].booking.id")
                        .value(org.hamcrest.Matchers.contains(nonCancelled.getId().intValue())))
                .andExpect(jsonPath("$.content[?(@.freight.id == " + freightWithFallback.getId() + ")].booking.bookingStatus")
                        .value(org.hamcrest.Matchers.contains("completed")))
                .andExpect(jsonPath("$.content[?(@.freight.id == " + freightWithAllCancelled.getId() + ")].booking.id")
                        .value(org.hamcrest.Matchers.contains(latestCancelled.getId().intValue())))
                .andExpect(jsonPath("$.content[?(@.freight.id == " + freightWithNoBooking.getId() + ")].booking")
                        .value(org.hamcrest.Matchers.contains(org.hamcrest.Matchers.nullValue())))
                .andExpect(jsonPath("$.content[?(@.freight.id == " + freightWithNoBooking.getId() + ")].trip")
                        .value(org.hamcrest.Matchers.contains(org.hamcrest.Matchers.nullValue())))
                .andExpect(jsonPath("$.content[?(@.freight.id == " + freightWithNoBooking.getId() + ")].payment")
                        .value(org.hamcrest.Matchers.contains(org.hamcrest.Matchers.nullValue())))
                .andExpect(jsonPath("$.content[?(@.freight.id == " + freightWithBookingOnly.getId() + ")].booking.id")
                        .value(org.hamcrest.Matchers.contains(bookingOnly.getId().intValue())))
                .andExpect(jsonPath("$.content[?(@.freight.id == " + freightWithBookingOnly.getId() + ")].trip")
                        .value(org.hamcrest.Matchers.contains(org.hamcrest.Matchers.nullValue())))
                .andExpect(jsonPath("$.content[?(@.freight.id == " + freightWithBookingOnly.getId() + ")].payment")
                        .value(org.hamcrest.Matchers.contains(org.hamcrest.Matchers.nullValue())))
                .andExpect(jsonPath("$.content[?(@.freight.id == " + freightWithTripOnly.getId() + ")].trip.id")
                        .value(org.hamcrest.Matchers.contains(trip.getId().intValue())))
                .andExpect(jsonPath("$.content[?(@.freight.id == " + freightWithTripOnly.getId() + ")].payment")
                        .value(org.hamcrest.Matchers.contains(org.hamcrest.Matchers.nullValue())));
    }

    @Test
    void pagesHaveStableBoundariesAndClampPageSize() throws Exception {
        User shipper = user(User.Role.SHIPPER);
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            ids.add(freight(shipper).getId());
        }
        entityManager.flush();
        entityManager.createNativeQuery("UPDATE freight SET created_at = :createdAt WHERE shipper_id = :shipperId")
                .setParameter("createdAt", LocalDateTime.of(2026, 1, 1, 12, 0))
                .setParameter("shipperId", shipper.getId())
                .executeUpdate();
        entityManager.clear();
        String token = bearer(shipper);

        mvc.perform(get("/history/shipper/freight").queryParam("page", "0").queryParam("size", "2")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.content[0].freight.id").value(ids.get(4)))
                .andExpect(jsonPath("$.content[1].freight.id").value(ids.get(3)));
        mvc.perform(get("/history/shipper/freight").queryParam("page", "1").queryParam("size", "2")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].freight.id").value(ids.get(2)))
                .andExpect(jsonPath("$.content[1].freight.id").value(ids.get(1)));
        mvc.perform(get("/history/shipper/freight").queryParam("page", "-1").queryParam("size", "0")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.content.length()").value(1));
        mvc.perform(get("/history/shipper/freight").queryParam("size", "1000")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100))
                .andExpect(jsonPath("$.content.length()").value(5));
    }

    @Test
    void historyUsesBoundedFreightSliceAndOnePageBoundedBookingQuery() throws Exception {
        User shipper = user(User.Role.SHIPPER);
        User driver = user(User.Role.DRIVER);
        Vehicle vehicle = vehicle(driver);
        for (int i = 0; i < 7; i++) {
            Freight freight = freight(shipper);
            for (int booking = 0; booking < 4; booking++) {
                booking(freight, vehicle, driver, Booking.BookingStatus.CANCELLED);
            }
        }
        entityManager.flush();
        SqlCapture.SQL.clear();

        mvc.perform(get("/history/shipper/freight").queryParam("size", "3")
                        .header("Authorization", bearer(shipper)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.hasNext").value(true));

        List<String> freightQueries = SqlCapture.SQL.stream()
                .filter(sql -> sql.toLowerCase().contains(" from freight "))
                .toList();
        List<String> bookingQueries = SqlCapture.SQL.stream()
                .filter(sql -> sql.toLowerCase().contains("ranked_bookings"))
                .toList();
        assertThat(freightQueries).hasSize(1);
        assertThat(freightQueries.get(0).toLowerCase()).contains("fetch first");
        assertThat(bookingQueries).hasSize(1);
        assertThat(bookingQueries.get(0).toLowerCase()).contains("cargo_id in");
        assertThat(SqlCapture.SQL).noneMatch(sql -> {
            String lower = sql.toLowerCase();
            return lower.contains("count(") && lower.contains("from freight");
        });
    }

    private User user(User.Role role) {
        String suffix = UUID.randomUUID().toString();
        return userRepository.saveAndFlush(User.builder()
                .fullName("History " + role)
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
                .truckType("History truck")
                .plateNumber("HIST-" + UUID.randomUUID().toString().substring(0, 8))
                .capacity(10.0)
                .build());
    }

    private Freight freight(User shipper) {
        return freightRepository.saveAndFlush(Freight.builder()
                .shipper(shipper)
                .pickupLocation("History pickup")
                .destination("History destination")
                .materialType("General")
                .cargoDescription("History test freight")
                .weightTons(BigDecimal.ONE)
                .budget(BigDecimal.valueOf(1000))
                .urgencyLevel("normal")
                .status(Freight.FreightStatus.COMPLETED)
                .build());
    }

    private Booking booking(
            Freight freight, Vehicle vehicle, User driver, Booking.BookingStatus status) {
        return bookingRepository.saveAndFlush(Booking.builder()
                .cargoRequest(freight)
                .vehicle(vehicle)
                .driver(driver)
                .bookingStatus(status)
                .commissionFee(BigDecimal.ZERO)
                .build());
    }

    private Trip trip(Booking booking, Vehicle vehicle, User driver) {
        return Trip.builder()
                .booking(booking)
                .driver(driver)
                .vehicle(vehicle)
                .startLocation("History origin")
                .destination("History destination")
                .startTime(LocalDateTime.of(2026, 1, 1, 12, 0))
                .tripStatus(Trip.TripStatus.COMPLETED)
                .build();
    }

    private Payment payment(Booking booking, User paidBy) {
        return paymentRepository.saveAndFlush(Payment.builder()
                .booking(booking)
                .amount(BigDecimal.valueOf(1000))
                .commissionAmount(BigDecimal.ZERO)
                .driverNetAmount(BigDecimal.valueOf(1000))
                .paymentMethod("cash")
                .paymentStatus(Payment.PaymentStatus.PENDING)
                .paidBy(paidBy)
                .build());
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generateToken(user);
    }

    public static class SqlCapture implements StatementInspector {
        static final List<String> SQL = new CopyOnWriteArrayList<>();

        @Override
        public String inspect(String sql) {
            String normalized = sql.stripLeading().toLowerCase();
            if (normalized.startsWith("select") || normalized.startsWith("with")) {
                SQL.add(sql);
            }
            return sql;
        }
    }
}
