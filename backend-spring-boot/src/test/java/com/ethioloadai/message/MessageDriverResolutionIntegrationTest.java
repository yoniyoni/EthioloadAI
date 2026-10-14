package com.ethioloadai.message;

import com.ethioloadai.booking.entity.Booking;
import com.ethioloadai.booking.repository.BookingRepository;
import com.ethioloadai.freight.entity.Freight;
import com.ethioloadai.freight.repository.FreightRepository;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.user.repository.UserRepository;
import com.ethioloadai.vehicle.entity.Vehicle;
import com.ethioloadai.vehicle.repository.VehicleRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.jpa.properties.hibernate.session_factory.statement_inspector=com.ethioloadai.message.MessageDriverResolutionIntegrationTest$SqlCapture"
})
@ActiveProfiles("test")
@Transactional
class MessageDriverResolutionIntegrationTest {

    @Autowired private BookingRepository bookingRepository;
    @Autowired private FreightRepository freightRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private VehicleRepository vehicleRepository;
    @Autowired private EntityManager entityManager;

    @BeforeEach
    void clearSqlCapture() {
        SqlCapture.SQL.clear();
    }

    @Test
    void acceptedDriversAreGroupedOrderedAndLimitedToTwo() {
        User shipper = user(User.Role.SHIPPER);
        User firstDriver = user(User.Role.DRIVER);
        User secondDriver = user(User.Role.DRIVER);
        User pendingDriver = user(User.Role.DRIVER);
        User cancelledDriver = user(User.Role.DRIVER);
        User confirmedDriver = user(User.Role.DRIVER);

        Freight freight = freight(shipper);
        booking(freight, firstDriver, Booking.BookingStatus.ACCEPTED);
        booking(freight, secondDriver, Booking.BookingStatus.ACCEPTED);
        booking(freight, firstDriver, Booking.BookingStatus.ACCEPTED);
        booking(freight, pendingDriver, Booking.BookingStatus.PENDING);
        booking(freight, cancelledDriver, Booking.BookingStatus.CANCELLED);
        booking(freight, confirmedDriver, Booking.BookingStatus.CONFIRMED);

        Freight onlyIneligibleFreight = freight(shipper);
        booking(onlyIneligibleFreight, pendingDriver, Booking.BookingStatus.PENDING);
        booking(onlyIneligibleFreight, cancelledDriver, Booking.BookingStatus.CANCELLED);
        booking(onlyIneligibleFreight, confirmedDriver, Booking.BookingStatus.CONFIRMED);

        entityManager.flush();
        entityManager.clear();
        SqlCapture.SQL.clear();

        List<User> drivers = bookingRepository.findDistinctDriversByCargoRequest_IdAndBookingStatus(
                freight.getId(), Booking.BookingStatus.ACCEPTED, PageRequest.of(0, 2));

        assertThat(drivers).extracting(User::getId)
                .containsExactly(firstDriver.getId(), secondDriver.getId());

        List<String> querySql = SqlCapture.SQL.stream()
                .filter(sql -> sql.toLowerCase().contains(" from bookings"))
                .toList();
        assertThat(querySql).hasSize(1);
        String sql = querySql.getFirst().toLowerCase();
        assertThat(sql).contains("group by", "max(", "order by");
        assertThat(sql).contains("fetch first ? rows only");

        assertThat(bookingRepository.findDistinctDriversByCargoRequest_IdAndBookingStatus(
                onlyIneligibleFreight.getId(), Booking.BookingStatus.ACCEPTED, PageRequest.of(0, 2)))
                .isEmpty();
    }

    private User user(User.Role role) {
        String suffix = UUID.randomUUID().toString();
        return userRepository.saveAndFlush(User.builder()
                .fullName("Message participant " + role)
                .phone("+" + suffix.substring(0, 12))
                .password("test-password")
                .role(role)
                .verificationStatus(true)
                .isActive(true)
                .build());
    }

    private Freight freight(User shipper) {
        return freightRepository.saveAndFlush(Freight.builder()
                .shipper(shipper)
                .pickupLocation("Message pickup")
                .destination("Message destination")
                .materialType("General")
                .cargoDescription("Message freight")
                .weightTons(BigDecimal.ONE)
                .budget(BigDecimal.valueOf(1000))
                .urgencyLevel("normal")
                .status(Freight.FreightStatus.MATCHED)
                .build());
    }

    private void booking(Freight freight, User driver, Booking.BookingStatus status) {
        Vehicle vehicle = vehicleRepository.saveAndFlush(Vehicle.builder()
                .userId(driver.getId())
                .truckType("Message test truck")
                .plateNumber("MSG-" + UUID.randomUUID().toString().substring(0, 8))
                .capacity(10.0)
                .build());
        bookingRepository.saveAndFlush(Booking.builder()
                .cargoRequest(freight)
                .vehicle(vehicle)
                .driver(driver)
                .bookingStatus(status)
                .estimatedPrice(BigDecimal.valueOf(1000))
                .commissionFee(BigDecimal.ZERO)
                .build());
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
