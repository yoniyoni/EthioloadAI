package com.ethioloadai.trip.service;

import com.ethioloadai.booking.repository.BookingRepository;
import com.ethioloadai.freight.repository.FreightRepository;
import com.ethioloadai.tracking.service.RouteBoundaryProtectionService;
import com.ethioloadai.tracking.service.VehicleTripConsistencyService;
import com.ethioloadai.trip.entity.Trip;
import com.ethioloadai.trip.repository.TripRepository;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TripServiceGpsBoundaryTest {

    @Mock private TripRepository tripRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private FreightRepository freightRepository;
    @Mock private VehicleRepository vehicleRepository;
    @Mock private TripInvariantService tripInvariantService;
    @Mock private TripLifecycleService tripLifecycleService;
    @Mock private TripStateMachine tripStateMachine;
    @Mock private TripMonitoringService tripMonitoringService;
    @Mock private TripStatusTransitionService tripStatusTransitionService;
    @Mock private TripStopService tripStopService;
    @Mock private RouteDataService routeDataService;
    @Mock private TripSecurityService tripSecurityService;
    @Mock private VehicleTripConsistencyService vehicleTripConsistencyService;
    @Mock private RouteBoundaryProtectionService routeBoundaryProtectionService;

    @InjectMocks private TripServiceImpl tripService;

    @Test
    void legacyGpsUpdateStillInvokesTrustedInternalBoundaryEnforcement() {
        User driver = User.builder().id(12L).role(User.Role.DRIVER).build();
        Trip trip = Trip.builder().id(41L).build();
        when(tripSecurityService.getAuthorizedTrip(41L, driver)).thenReturn(Optional.of(trip));
        when(routeDataService.addGpsLocation(eq(trip), any(), eq(driver))).thenReturn(trip);
        when(routeBoundaryProtectionService.enforceRouteBoundaries(trip)).thenReturn(
                new RouteBoundaryProtectionService.BoundaryEnforcementResult(
                        false, List.of(), 0, 0, 0, null, null, 0, "NO_ACTION_NEEDED"));

        Map<String, Object> gpsData = new HashMap<>();
        gpsData.put("lat", "9.03");
        gpsData.put("lng", "38.74");
        Trip updated = tripService.updateLocation(41L, gpsData, driver);

        assertThat(updated).isSameAs(trip);
        verify(routeDataService).addGpsLocation(eq(trip), any(), eq(driver));
        verify(routeBoundaryProtectionService).enforceRouteBoundaries(trip);
    }
}
