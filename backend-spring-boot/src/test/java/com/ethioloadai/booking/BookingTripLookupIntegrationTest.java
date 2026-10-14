package com.ethioloadai.booking;

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
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class BookingTripLookupIntegrationTest {
    @Autowired private BookingRepository bookingRepository;
    @Autowired private FreightRepository freightRepository;
    @Autowired private TripRepository tripRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private VehicleRepository vehicleRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private JwtService jwtService;
    @Autowired private MockMvc mvc;

    @Test
    void shipperDriverAndAdminResolveTheExactTripForTheSelectedBooking() throws Exception {
        User shipper = user(User.Role.SHIPPER);
        User driver = user(User.Role.DRIVER);
        User otherDriver = user(User.Role.DRIVER);
        User admin = user(User.Role.ADMIN);
        Freight cargo = freight(shipper);
        Booking selected = booking(cargo, driver);
        Booking otherBookingForSameFreight = booking(cargo, otherDriver);
        Trip selectedTrip = trip(selected);
        trip(otherBookingForSameFreight);
        entityManager.flush();
        entityManager.clear();

        for (User caller : new User[]{shipper, driver, admin}) {
            mvc.perform(get("/bookings/{id}/trip", selected.getId())
                            .header("Authorization", bearer(caller)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.bookingId").value(selected.getId()))
                    .andExpect(jsonPath("$.freightId").value(cargo.getId()))
                    .andExpect(jsonPath("$.bookingStatus").value("accepted"))
                    .andExpect(jsonPath("$.tripId").value(selectedTrip.getId()))
                    .andExpect(jsonPath("$.tripStatus").value("ongoing"))
                    .andExpect(jsonPath("$.tripType").value("single"))
                    .andExpect(jsonPath("$.serviceType").value("intercity"));
        }

        mvc.perform(get("/bookings/{id}", selected.getId())
                        .header("Authorization", bearer(shipper)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tripId").value(selectedTrip.getId()))
                .andExpect(jsonPath("$.tripStatus").value("ongoing"));
    }

    @Test
    void unauthorizedShipperAndDriverCannotResolveBookingTrip() throws Exception {
        User owner = user(User.Role.SHIPPER);
        User otherShipper = user(User.Role.SHIPPER);
        User assignedDriver = user(User.Role.DRIVER);
        User otherDriver = user(User.Role.DRIVER);
        Booking booking = booking(freight(owner), assignedDriver);
        trip(booking);
        entityManager.flush();

        mvc.perform(get("/bookings/{id}/trip", booking.getId())
                        .header("Authorization", bearer(otherShipper)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/bookings/{id}/trip", booking.getId())
                        .header("Authorization", bearer(otherDriver)))
                .andExpect(status().isForbidden());
    }

    @Test
    void missingTripIsExplicitAndCallerSuppliedIdsCannotSelectAnotherRelationship() throws Exception {
        User shipper = user(User.Role.SHIPPER);
        User assignedDriver = user(User.Role.DRIVER);
        User otherDriver = user(User.Role.DRIVER);
        Freight cargo = freight(shipper);
        Booking selectedWithoutTrip = booking(cargo, assignedDriver);
        Booking otherBooking = booking(cargo, otherDriver);
        Trip otherTrip = trip(otherBooking);
        entityManager.flush();
        entityManager.clear();

        mvc.perform(get("/bookings/{id}/trip", selectedWithoutTrip.getId())
                        .queryParam("tripId", otherTrip.getId().toString())
                        .queryParam("driverId", otherDriver.getId().toString())
                        .queryParam("shipperId", user(User.Role.SHIPPER).getId().toString())
                        .header("Authorization", bearer(shipper)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId").value(selectedWithoutTrip.getId()))
                .andExpect(jsonPath("$.freightId").value(cargo.getId()))
                .andExpect(jsonPath("$.tripId").value(nullValue()))
                .andExpect(jsonPath("$.tripStatus").value(nullValue()))
                .andExpect(jsonPath("$.tripType").value(nullValue()))
                .andExpect(jsonPath("$.serviceType").value("intercity"));
    }

    @Test
    void resolverRequiresAuthenticationAndDoesNotAcceptAnArbitraryTripId() throws Exception {
        User shipper = user(User.Role.SHIPPER);
        User driver = user(User.Role.DRIVER);
        Booking booking = booking(freight(shipper), driver);
        Trip expected = trip(booking);
        entityManager.flush();
        entityManager.clear();

        mvc.perform(get("/bookings/{id}/trip", booking.getId()))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/bookings/{id}/trip", booking.getId())
                        .queryParam("tripId", Long.toString(expected.getId() + 1000))
                        .header("Authorization", bearer(shipper)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId").value(booking.getId()))
                .andExpect(jsonPath("$.tripId").value(expected.getId()));
    }

    private User user(User.Role role) {
        String suffix = UUID.randomUUID().toString();
        return userRepository.saveAndFlush(User.builder()
                .fullName("Trip lookup " + role)
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
                .pickupLocation("Pickup")
                .destination("Destination")
                .materialType("General")
                .weightTons(BigDecimal.ONE)
                .budget(BigDecimal.valueOf(1000))
                .urgencyLevel("normal")
                .build());
    }

    private Vehicle vehicle(User driver) {
        return vehicleRepository.saveAndFlush(Vehicle.builder()
                .userId(driver.getId())
                .truckType("Trip lookup truck")
                .plateNumber("LOOKUP-" + UUID.randomUUID().toString().substring(0, 8))
                .capacity(10.0)
                .build());
    }

    private Booking booking(Freight cargo, User driver) {
        return bookingRepository.saveAndFlush(Booking.builder()
                .cargoRequest(cargo)
                .vehicle(vehicle(driver))
                .driver(driver)
                .bookingStatus(Booking.BookingStatus.ACCEPTED)
                .commissionFee(BigDecimal.ZERO)
                .build());
    }

    private Trip trip(Booking booking) {
        return tripRepository.saveAndFlush(Trip.builder()
                .booking(booking)
                .driver(booking.getDriver())
                .vehicle(booking.getVehicle())
                .startLocation("Pickup")
                .destination("Destination")
                .tripStatus(Trip.TripStatus.ONGOING)
                .tripType(Trip.TripType.SINGLE)
                .build());
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generateToken(user);
    }
}
