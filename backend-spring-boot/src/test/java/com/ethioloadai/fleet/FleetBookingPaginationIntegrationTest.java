package com.ethioloadai.fleet;

import com.ethioloadai.booking.entity.Booking;
import com.ethioloadai.booking.repository.BookingRepository;
import com.ethioloadai.freight.entity.Freight;
import com.ethioloadai.freight.repository.FreightRepository;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.user.repository.UserRepository;
import com.ethioloadai.vehicle.entity.Vehicle;
import com.ethioloadai.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class FleetBookingPaginationIntegrationTest {

    private static final List<Booking.BookingStatus> EXCLUDED_STATUSES =
            List.of(Booking.BookingStatus.COMPLETED, Booking.BookingStatus.CANCELLED);

    @Autowired private BookingRepository bookingRepository;
    @Autowired private FreightRepository freightRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private VehicleRepository vehicleRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private User fleetDriver;
    private Freight cancelledFreight;
    private Vehicle vehicle;

    @BeforeEach
    void setUp() {
        User shipper = user(User.Role.SHIPPER, null);
        User owner = user(User.Role.FLEET_OWNER, null);
        fleetDriver = user(User.Role.DRIVER, owner.getId());
        User unrelatedDriver = user(User.Role.DRIVER, null);
        cancelledFreight = freightRepository.saveAndFlush(Freight.builder()
                .shipper(shipper)
                .pickupLocation("Projection pickup")
                .destination("Projection destination")
                .materialType("General")
                .cargoDescription("Fleet dashboard test")
                .weightTons(BigDecimal.ONE)
                .budget(BigDecimal.valueOf(1000))
                .urgencyLevel("normal")
                .status(Freight.FreightStatus.CANCELLED)
                .build());
        vehicle = vehicle(fleetDriver);

        LocalDateTime older = LocalDateTime.of(2025, 1, 1, 10, 0);
        LocalDateTime newest = LocalDateTime.of(2025, 1, 2, 10, 0);
        createBooking(Booking.BookingStatus.PENDING, fleetDriver, older);
        createBooking(Booking.BookingStatus.ACCEPTED, fleetDriver, older);
        Booking delivered = createBooking(Booking.BookingStatus.DELIVERED, fleetDriver, newest);
        Booking confirmed = createBooking(Booking.BookingStatus.CONFIRMED, fleetDriver, newest);
        createBooking(Booking.BookingStatus.COMPLETED, fleetDriver, newest.plusDays(1));
        createBooking(Booking.BookingStatus.CANCELLED, fleetDriver, newest.plusDays(2));
        createBooking(Booking.BookingStatus.ACCEPTED, unrelatedDriver, newest.plusDays(3));
        jdbcTemplate.update("UPDATE bookings SET created_at = ? WHERE id = ?",
                Timestamp.valueOf(newest), delivered.getId());
        jdbcTemplate.update("UPDATE bookings SET created_at = ? WHERE id = ?",
                Timestamp.valueOf(newest), confirmed.getId());
    }

    @Test
    void countAndSliceUseFleetStatusScopeAndStableProjectedOrdering() {
        List<Long> fleetDriverIds = List.of(fleetDriver.getId());

        long count = bookingRepository.countActiveByDriverIdIn(fleetDriverIds, EXCLUDED_STATUSES);
        Slice<BookingRepository.FleetActiveBookingProjection> firstPage =
                bookingRepository.findActiveDashboardBookings(
                        fleetDriverIds, EXCLUDED_STATUSES, PageRequest.of(0, 2));
        Slice<BookingRepository.FleetActiveBookingProjection> secondPage =
                bookingRepository.findActiveDashboardBookings(
                        fleetDriverIds, EXCLUDED_STATUSES, PageRequest.of(1, 2));

        assertThat(count).isEqualTo(4);
        assertThat(firstPage.getContent()).hasSize(2);
        assertThat(firstPage.hasNext()).isTrue();
        assertThat(secondPage.getContent()).hasSize(2);
        assertThat(secondPage.hasNext()).isFalse();

        var first = firstPage.getContent().get(0);
        var second = firstPage.getContent().get(1);
        assertThat(first.getBookingId()).isGreaterThan(second.getBookingId());
        assertThat(first.getStatus()).isEqualTo(Booking.BookingStatus.CONFIRMED);
        assertThat(second.getStatus()).isEqualTo(Booking.BookingStatus.DELIVERED);
        assertThat(first.getDriverId()).isEqualTo(fleetDriver.getId());
        assertThat(first.getDriverName()).isEqualTo(fleetDriver.getFullName());
        assertThat(first.getPickupLocation()).isEqualTo("Projection pickup");
        assertThat(first.getDestination()).isEqualTo("Projection destination");
        assertThat(secondPage.getContent())
                .extracting(BookingRepository.FleetActiveBookingProjection::getStatus)
                .containsExactly(Booking.BookingStatus.ACCEPTED, Booking.BookingStatus.PENDING);
    }

    private User user(User.Role role, Long fleetOwnerId) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return userRepository.saveAndFlush(User.builder()
                .fullName("Fleet query " + role + " " + suffix)
                .phone("+251" + suffix)
                .password("test-password")
                .role(role)
                .fleetOwnerId(fleetOwnerId)
                .verificationStatus(true)
                .isActive(true)
                .build());
    }

    private Vehicle vehicle(User driver) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return vehicleRepository.saveAndFlush(Vehicle.builder()
                .userId(driver.getId())
                .truckType("Fleet test truck")
                .plateNumber("FLT-" + suffix)
                .capacity(10.0)
                .build());
    }

    private Booking createBooking(
            Booking.BookingStatus status, User driver, LocalDateTime createdAt) {
        Booking booking = bookingRepository.saveAndFlush(Booking.builder()
                .cargoRequest(cancelledFreight)
                .vehicle(vehicle)
                .driver(driver)
                .bookingStatus(status)
                .estimatedPrice(BigDecimal.valueOf(1000))
                .commissionFee(BigDecimal.ZERO)
                .build());
        jdbcTemplate.update("UPDATE bookings SET created_at = ? WHERE id = ?",
                Timestamp.valueOf(createdAt), booking.getId());
        return booking;
    }
}
