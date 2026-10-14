package com.ethioloadai.websocket.security;

import com.ethioloadai.trip.repository.TripRepository;
import com.ethioloadai.booking.repository.BookingRepository;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.vehicle.entity.Vehicle;
import com.ethioloadai.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebSocketAuthorizationServiceImplTest {

    private final WebSocketSecurityConfig securityConfig = new WebSocketSecurityConfig();
    private final TripRepository tripRepository = mock(TripRepository.class);
    private final BookingRepository bookingRepository = mock(BookingRepository.class);
    private final VehicleRepository vehicleRepository = mock(VehicleRepository.class);
    private final WebSocketAuthorizationServiceImpl authorizationService =
        new WebSocketAuthorizationServiceImpl(
            securityConfig, tripRepository, bookingRepository, vehicleRepository
        );

    @Test
    void aggregateFleetTopicIsDeniedForFleetOwnersAndAdmins() {
        User fleetOwner = user(1L, User.Role.FLEET_OWNER);
        User admin = user(2L, User.Role.ADMIN);

        assertThat(authorizationService.canSubscribeToTopic(fleetOwner, "fleet.vehicles", null).allowed())
            .isFalse();
        assertThat(authorizationService.canSubscribeToTopic(admin, "fleet.vehicles", null).allowed())
            .isFalse();
    }

    @Test
    void fleetOwnerCanStillSubscribeToOwnVehicleTopic() {
        User fleetOwner = user(1L, User.Role.FLEET_OWNER);
        Vehicle vehicle = new Vehicle();
        vehicle.setId(10L);
        vehicle.setFleetOwnerId(fleetOwner.getId());
        when(vehicleRepository.findByFleetOwnerId(fleetOwner.getId())).thenReturn(List.of(vehicle));

        assertThat(authorizationService.canSubscribeToTopic(fleetOwner, "vehicle.10.location", null).allowed())
            .isTrue();
        assertThat(authorizationService.canSubscribeToTopic(fleetOwner, "vehicle.11.location", null).allowed())
            .isFalse();
    }

    @Test
    void adminTripAndUserTopicsRemainAvailable() {
        User admin = user(2L, User.Role.ADMIN);

        assertThat(authorizationService.canSubscribeToTopic(admin, "trip.10.location", null).allowed())
            .isTrue();
        assertThat(authorizationService.canSubscribeToTopic(admin, "user.1.notifications", null).allowed())
            .isTrue();
    }

    private User user(Long id, User.Role role) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        return user;
    }
}
