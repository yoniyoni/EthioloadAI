package com.ethioloadai.dashboard;

import com.ethioloadai.booking.entity.Booking;
import com.ethioloadai.booking.repository.BookingRepository;
import com.ethioloadai.freight.entity.Freight;
import com.ethioloadai.freight.repository.FreightRepository;
import com.ethioloadai.security.jwt.JwtService;
import com.ethioloadai.trip.entity.Trip;
import com.ethioloadai.trip.repository.TripRepository;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.user.repository.UserRepository;
import com.ethioloadai.vehicle.entity.Vehicle;
import com.ethioloadai.vehicle.repository.VehicleRepository;
import jakarta.persistence.EntityManager;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class DashboardSummaryIntegrationTest {
    @Autowired private FreightRepository freightRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private TripRepository tripRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private VehicleRepository vehicleRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private JwtService jwtService;
    @Autowired private MockMvc mvc;

    @Test
    void summaryUsesOwnerScopedCountsAndReturnsBoundedDeterministicallyOrderedFreight() throws Exception {
        User shipper = user(User.Role.SHIPPER);
        User otherShipper = user(User.Role.SHIPPER);
        List<Freight> owned = List.of(
                freight(shipper, Freight.FreightStatus.POSTED),
                freight(shipper, Freight.FreightStatus.MATCHED),
                freight(shipper, Freight.FreightStatus.IN_TRANSIT),
                freight(shipper, Freight.FreightStatus.DELIVERED),
                freight(shipper, Freight.FreightStatus.COMPLETED),
                freight(shipper, Freight.FreightStatus.CANCELLED));
        freight(otherShipper, Freight.FreightStatus.POSTED);
        entityManager.flush();
        entityManager.createNativeQuery("UPDATE freight SET created_at = :createdAt WHERE shipper_id = :shipperId")
                .setParameter("createdAt", LocalDateTime.of(2026, 1, 1, 12, 0))
                .setParameter("shipperId", shipper.getId())
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();

        mvc.perform(get("/dashboard/summary").header("Authorization", bearer(shipper)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalFreightPosts").value(6))
                .andExpect(jsonPath("$.operationalShipments").value(3))
                .andExpect(jsonPath("$.completedFreight").value(1))
                .andExpect(jsonPath("$.cancelledFreight").value(1))
                .andExpect(jsonPath("$.recentFreight.length()").value(5))
                .andExpect(jsonPath("$.recentFreight[0].id").value(owned.get(5).getId()))
                .andExpect(jsonPath("$.recentOperationalFreight.length()").value(3))
                .andExpect(jsonPath("$.recentOperationalFreight[0].id").value(owned.get(2).getId()))
                .andExpect(jsonPath("$.totalBookings").value(0))
                .andExpect(jsonPath("$.pendingBookings").value(0))
                .andExpect(jsonPath("$.activeBookings").value(0))
                .andExpect(jsonPath("$.activeTrips").value(0))
                .andExpect(jsonPath("$.recentBookings.length()").value(0))
                .andExpect(jsonPath("$.recentTrips.length()").value(0));
    }

    @Test
    void summaryCountsOnlyNonterminalBookingsAndTripsAndBoundsRecentActivity() throws Exception {
        User shipper = user(User.Role.SHIPPER);
        User driver = user(User.Role.DRIVER);
        User otherShipper = user(User.Role.SHIPPER);
        Vehicle vehicle = vehicle(driver);
        Freight cargo = freight(shipper, Freight.FreightStatus.MATCHED);
        Freight otherCargo = freight(otherShipper, Freight.FreightStatus.MATCHED);

        List<Booking> bookings = List.of(
                booking(cargo, vehicle, driver, Booking.BookingStatus.PENDING),
                booking(cargo, vehicle, driver, Booking.BookingStatus.CONFIRMED),
                booking(cargo, vehicle, driver, Booking.BookingStatus.COMPLETED),
                booking(cargo, vehicle, driver, Booking.BookingStatus.CANCELLED));
        Booking otherBooking = booking(
                otherCargo, vehicle, driver, Booking.BookingStatus.PENDING);
        List<Trip> trips = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Trip.TripStatus tripStatus = i < 2
                    ? Trip.TripStatus.ONGOING
                    : Trip.TripStatus.COMPLETED;
            trips.add(tripRepository.save(trip(bookings.get(i), vehicle, driver, tripStatus)));
        }
        tripRepository.save(trip(otherBooking, vehicle, driver, Trip.TripStatus.ONGOING));
        entityManager.flush();
        entityManager.createNativeQuery("UPDATE bookings SET created_at = :createdAt")
                .setParameter("createdAt", LocalDateTime.of(2026, 1, 1, 12, 0))
                .executeUpdate();
        entityManager.createNativeQuery("UPDATE trips SET start_time = :startTime")
                .setParameter("startTime", LocalDateTime.of(2026, 1, 1, 12, 0))
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();

        mvc.perform(get("/dashboard/summary").header("Authorization", bearer(shipper)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeBookings").value(2))
                .andExpect(jsonPath("$.activeTrips").value(2))
                .andExpect(jsonPath("$.recentBookings.length()").value(3))
                .andExpect(jsonPath("$.recentBookings[0].id").value(bookings.get(3).getId()))
                .andExpect(jsonPath("$.recentTrips.length()").value(3))
                .andExpect(jsonPath("$.recentTrips[0].id").value(trips.get(3).getId()));
    }

    @Test
    void shipperSummaryReturnsPrincipalScopedLifetimeTotalAndPendingCounts() throws Exception {
        User shipper = user(User.Role.SHIPPER);
        User otherShipper = user(User.Role.SHIPPER);
        User driver = user(User.Role.DRIVER);
        Vehicle vehicle = vehicle(driver);
        Freight ownedFreight = freight(shipper, Freight.FreightStatus.MATCHED);
        Freight otherFreight = freight(otherShipper, Freight.FreightStatus.MATCHED);

        booking(ownedFreight, vehicle, driver, Booking.BookingStatus.PENDING);
        booking(ownedFreight, vehicle, driver, Booking.BookingStatus.CONFIRMED);
        booking(ownedFreight, vehicle, driver, Booking.BookingStatus.COMPLETED);
        booking(ownedFreight, vehicle, driver, Booking.BookingStatus.CANCELLED);
        booking(otherFreight, vehicle, driver, Booking.BookingStatus.PENDING);
        entityManager.flush();
        entityManager.clear();

        mvc.perform(get("/dashboard/summary").header("Authorization", bearer(shipper)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalBookings").value(4))
                .andExpect(jsonPath("$.pendingBookings").value(1))
                .andExpect(jsonPath("$.activeBookings").value(2));

        mvc.perform(get("/dashboard/summary").header("Authorization", bearer(user(User.Role.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalBookings").value(0))
                .andExpect(jsonPath("$.pendingBookings").value(0))
                .andExpect(jsonPath("$.activeBookings").value(0));
    }

    @Test
    void driverSummaryUsesPrincipalCountsAndReturnsOnlyThreeOrderedPendingRows() throws Exception {
        User shipper = user(User.Role.SHIPPER);
        User driver = user(User.Role.DRIVER);
        User otherDriver = user(User.Role.DRIVER);
        Vehicle vehicle = vehicle(driver);
        Vehicle otherVehicle = vehicle(otherDriver);
        Freight cargo = freight(shipper, Freight.FreightStatus.MATCHED);

        List<Booking> pending = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            pending.add(booking(cargo, vehicle, driver, Booking.BookingStatus.PENDING));
        }
        booking(cargo, vehicle, driver, Booking.BookingStatus.CONFIRMED);
        booking(cargo, vehicle, driver, Booking.BookingStatus.COMPLETED);
        booking(cargo, vehicle, driver, Booking.BookingStatus.ACCEPTED);
        booking(cargo, vehicle, driver, Booking.BookingStatus.DELIVERED);
        booking(cargo, otherVehicle, otherDriver, Booking.BookingStatus.PENDING);
        entityManager.flush();
        entityManager.createNativeQuery("UPDATE bookings SET created_at = :createdAt")
                .setParameter("createdAt", LocalDateTime.of(2026, 1, 1, 12, 0))
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();

        String driverToken = bearer(driver);
        mvc.perform(get("/dashboard/driver-summary").header("Authorization", driverToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalBookings").value(9))
                .andExpect(jsonPath("$.confirmedBookings").value(1))
                .andExpect(jsonPath("$.completedBookings").value(1))
                .andExpect(jsonPath("$.pendingBookings").value(5))
                .andExpect(jsonPath("$.pendingBookingRows.length()").value(3))
                .andExpect(jsonPath("$.pendingBookingRows[0].id").value(pending.get(4).getId()))
                .andExpect(jsonPath("$.pendingBookingRows[1].id").value(pending.get(3).getId()))
                .andExpect(jsonPath("$.pendingBookingRows[2].id").value(pending.get(2).getId()))
                .andExpect(jsonPath("$.pendingBookingRows[0].driverId").value(driver.getId()));

        mvc.perform(get("/dashboard/driver-summary")
                        .queryParam("driverId", otherDriver.getId().toString())
                        .header("Authorization", driverToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalBookings").value(9))
                .andExpect(jsonPath("$.pendingBookings").value(5))
                .andExpect(jsonPath("$.pendingBookingRows[0].driverId").value(driver.getId()));
    }

    @Test
    void summaryRequiresAuthenticationAndShipperOrAdminRole() throws Exception {
        mvc.perform(get("/dashboard/summary"))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/dashboard/summary")
                        .header("Authorization", bearer(user(User.Role.DRIVER))))
                .andExpect(status().isForbidden());
    }

    @Test
    void driverSummaryRequiresAuthenticationAndDriverRole() throws Exception {
        mvc.perform(get("/dashboard/driver-summary"))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/dashboard/driver-summary")
                        .header("Authorization", bearer(user(User.Role.SHIPPER))))
                .andExpect(status().isForbidden());

        mvc.perform(get("/dashboard/driver-summary")
                        .header("Authorization", bearer(user(User.Role.ADMIN))))
                .andExpect(status().isForbidden());
    }

    private User user(User.Role role) {
        String suffix = UUID.randomUUID().toString();
        return userRepository.saveAndFlush(User.builder()
                .fullName("Dashboard " + role)
                .phone("+" + suffix.substring(0, 12))
                .password("test-password")
                .role(role)
                .verificationStatus(true)
                .isActive(true)
                .build());
    }

    private Freight freight(User shipper, Freight.FreightStatus status) {
        return freightRepository.saveAndFlush(Freight.builder()
                .shipper(shipper)
                .pickupLocation("Pickup")
                .destination("Destination")
                .materialType("General")
                .cargoDescription("Dashboard test freight")
                .weightTons(BigDecimal.ONE)
                .budget(BigDecimal.valueOf(1000))
                .urgencyLevel("normal")
                .status(status)
                .build());
    }

    private Vehicle vehicle(User driver) {
        return vehicleRepository.saveAndFlush(Vehicle.builder()
                .userId(driver.getId())
                .truckType("Dashboard truck")
                .plateNumber("DASH-" + UUID.randomUUID().toString().substring(0, 8))
                .capacity(10.0)
                .build());
    }

    private Booking booking(
            Freight cargo, Vehicle vehicle, User driver, Booking.BookingStatus status) {
        return bookingRepository.saveAndFlush(Booking.builder()
                .cargoRequest(cargo)
                .vehicle(vehicle)
                .driver(driver)
                .bookingStatus(status)
                .commissionFee(BigDecimal.ZERO)
                .build());
    }

    private Trip trip(
            Booking booking, Vehicle vehicle, User driver, Trip.TripStatus status) {
        return Trip.builder()
                .booking(booking)
                .vehicle(vehicle)
                .driver(driver)
                .startLocation("Origin")
                .destination("Destination")
                .startTime(LocalDateTime.of(2026, 1, 1, 12, 0))
                .tripStatus(status)
                .build();
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generateToken(user);
    }
}
