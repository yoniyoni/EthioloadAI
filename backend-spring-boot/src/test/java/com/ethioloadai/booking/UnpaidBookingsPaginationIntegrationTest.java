package com.ethioloadai.booking;

import com.ethioloadai.booking.entity.Booking;
import com.ethioloadai.booking.repository.BookingRepository;
import com.ethioloadai.freight.entity.Freight;
import com.ethioloadai.freight.repository.FreightRepository;
import com.ethioloadai.payment.entity.Payment;
import com.ethioloadai.payment.repository.PaymentRepository;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
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
        "spring.jpa.properties.hibernate.session_factory.statement_inspector=com.ethioloadai.booking.UnpaidBookingsPaginationIntegrationTest$SqlCapture"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class UnpaidBookingsPaginationIntegrationTest {

    @Autowired private BookingRepository bookingRepository;
    @Autowired private FreightRepository freightRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private VehicleRepository vehicleRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private JwtService jwtService;
    @Autowired private MockMvc mvc;

    @BeforeEach
    void clearSqlCapture() {
        SqlCapture.SQL.clear();
    }

    @Test
    void unpaidEndpointsAreBoundedStableCountedAndPreserveBothRouteAliases() throws Exception {
        User admin = user(User.Role.ADMIN);
        User shipper = user(User.Role.SHIPPER);
        User driver = user(User.Role.DRIVER);
        Vehicle vehicle = vehicle(driver);
        Freight freight = freight(shipper);
        LocalDateTime sameCreatedAt = LocalDateTime.of(2025, 2, 3, 12, 0);
        List<Long> eligibleIds = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            eligibleIds.add(booking(freight, vehicle, driver, Booking.BookingStatus.ACCEPTED, sameCreatedAt).getId());
        }

        booking(freight, vehicle, driver, Booking.BookingStatus.CANCELLED, sameCreatedAt);
        payment(booking(freight, vehicle, driver, Booking.BookingStatus.ACCEPTED, sameCreatedAt),
                Payment.PaymentStatus.PENDING);
        payment(booking(freight, vehicle, driver, Booking.BookingStatus.DELIVERED, sameCreatedAt),
                Payment.PaymentStatus.FAILED);
        payment(booking(freight, vehicle, driver, Booking.BookingStatus.COMPLETED, sameCreatedAt),
                Payment.PaymentStatus.PAID);

        entityManager.flush();
        entityManager.clear();
        SqlCapture.SQL.clear();
        String token = bearer(admin);

        mvc.perform(get("/admin/bookings/unpaid").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(6))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.pageSize").value(20))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.bookings.length()").value(6))
                .andExpect(jsonPath("$.bookings[0].id").value(eligibleIds.get(5)))
                .andExpect(jsonPath("$.bookings[0].estimated_price").value(1000))
                .andExpect(jsonPath("$.bookings[0].booking_status").value("accepted"))
                .andExpect(jsonPath("$.bookings[0].route").value("Unpaid pickup → Unpaid destination"))
                .andExpect(jsonPath("$.bookings[0].driver.name").value(driver.getFullName()))
                .andExpect(jsonPath("$.bookings[0].shipper.name").value(shipper.getFullName()));

        mvc.perform(get("/bookings/unpaid").header("Authorization", token)
                        .queryParam("page", "1").queryParam("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(6))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.pageSize").value(2))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.bookings.length()").value(2))
                .andExpect(jsonPath("$.bookings[0].id").value(eligibleIds.get(3)))
                .andExpect(jsonPath("$.bookings[1].id").value(eligibleIds.get(2)));

        mvc.perform(get("/admin/bookings/unpaid").header("Authorization", token)
                        .queryParam("page", "2").queryParam("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(6))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.bookings.length()").value(2))
                .andExpect(jsonPath("$.bookings[0].id").value(eligibleIds.get(1)))
                .andExpect(jsonPath("$.bookings[1].id").value(eligibleIds.get(0)));

        mvc.perform(get("/bookings/unpaid").header("Authorization", token).queryParam("size", "1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageSize").value(100));
        mvc.perform(get("/bookings/unpaid").header("Authorization", token).queryParam("page", "-1"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/bookings/unpaid").header("Authorization", token).queryParam("size", "0"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/bookings/unpaid")).andExpect(status().isUnauthorized());

        List<String> bookingQueries = SqlCapture.SQL.stream()
                .filter(sql -> sql.toLowerCase().contains(" from bookings"))
                .toList();
        assertThat(bookingQueries).anySatisfy(sql -> {
            String lower = sql.toLowerCase();
            assertThat(lower).contains("not exists", "payments", "booking_status", "order by", "left join");
            assertThat(lower).contains("fetch first");
        });
        assertThat(bookingQueries).anySatisfy(sql -> {
            String lower = sql.toLowerCase();
            assertThat(lower).contains("count(", "not exists", "payments", "booking_status");
            assertThat(lower).doesNotContain("left join");
        });
    }

    private User user(User.Role role) {
        String suffix = UUID.randomUUID().toString();
        return userRepository.saveAndFlush(User.builder()
                .fullName("Unpaid test " + role)
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
                .truckType("Unpaid test truck")
                .plateNumber("UNP-" + UUID.randomUUID().toString().substring(0, 8))
                .capacity(10.0)
                .build());
    }

    private Freight freight(User shipper) {
        return freightRepository.saveAndFlush(Freight.builder()
                .shipper(shipper)
                .pickupLocation("Unpaid pickup")
                .destination("Unpaid destination")
                .materialType("General")
                .cargoDescription("Unpaid test freight")
                .weightTons(BigDecimal.ONE)
                .budget(BigDecimal.valueOf(1000))
                .urgencyLevel("normal")
                .status(Freight.FreightStatus.MATCHED)
                .build());
    }

    private Booking booking(
            Freight freight,
            Vehicle vehicle,
            User driver,
            Booking.BookingStatus status,
            LocalDateTime createdAt) {
        Booking booking = bookingRepository.saveAndFlush(Booking.builder()
                .cargoRequest(freight)
                .vehicle(vehicle)
                .driver(driver)
                .bookingStatus(status)
                .estimatedPrice(BigDecimal.valueOf(1000))
                .commissionFee(BigDecimal.valueOf(100))
                .build());
        jdbcTemplate.update("UPDATE bookings SET created_at = ? WHERE id = ?",
                Timestamp.valueOf(createdAt), booking.getId());
        return booking;
    }

    private Payment payment(Booking booking, Payment.PaymentStatus status) {
        return paymentRepository.saveAndFlush(Payment.builder()
                .booking(booking)
                .amount(BigDecimal.valueOf(1000))
                .commissionAmount(BigDecimal.valueOf(100))
                .driverNetAmount(BigDecimal.valueOf(900))
                .paymentMethod("cash")
                .paymentStatus(status)
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
