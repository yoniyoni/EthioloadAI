package com.ethioloadai.tracking.controller;

import com.ethioloadai.tracking.service.RouteBoundaryProtectionService;
import com.ethioloadai.trip.entity.Trip;
import com.ethioloadai.trip.service.TripService;
import com.ethioloadai.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RouteBoundaryAuthorizationIntegrationTest {

    private static final String BASE_PATH = "/v1/tracking/route-boundaries/trips/41";

    @Autowired private MockMvc mockMvc;
    @MockBean private TripService tripService;
    @MockBean private RouteBoundaryProtectionService boundaryProtectionService;

    @Test
    void adminCanInvokeAllDirectOperations() throws Exception {
        Trip trip = Trip.builder().id(41L).build();
        when(tripService.getTripWithAuthorization(eq(41L), any(User.class))).thenReturn(trip);
        when(boundaryProtectionService.enforceRouteBoundaries(trip)).thenReturn(
                new RouteBoundaryProtectionService.BoundaryEnforcementResult(
                        false, List.of(), 0, 0, 0, null, null, 0, "NO_ACTION_NEEDED"));
        when(boundaryProtectionService.compressRoute(trip, 5)).thenReturn(
                new RouteBoundaryProtectionService.RouteCompressionResult(
                        0, 0, 0, 1, 1, "NONE", 0, List.of()));
        when(boundaryProtectionService.archiveOldRouteData(trip, 24)).thenReturn(
                new RouteBoundaryProtectionService.RouteArchivalResult(
                        0, 0, null, null, 0, "TRIP_ROUTE_DATA", true));
        when(boundaryProtectionService.performEmergencyCleanup(trip, 7L)).thenReturn(
                new RouteBoundaryProtectionService.EmergencyCleanupResult(
                        false, "NO_CLEANUP_NEEDED", 0, 0, 0, List.of(), "", true));

        var admin = userAuthentication(User.Role.ADMIN);
        mockMvc.perform(post(BASE_PATH + "/enforce").with(authentication(admin)))
                .andExpect(status().isOk());
        mockMvc.perform(post(BASE_PATH + "/compress").with(authentication(admin)))
                .andExpect(status().isOk());
        mockMvc.perform(post(BASE_PATH + "/archive").with(authentication(admin)))
                .andExpect(status().isOk());
        mockMvc.perform(post(BASE_PATH + "/emergency-cleanup?confirm=true").with(authentication(admin)))
                .andExpect(status().isOk());

        verify(boundaryProtectionService).enforceRouteBoundaries(trip);
        verify(boundaryProtectionService).compressRoute(trip, 5);
        verify(boundaryProtectionService).archiveOldRouteData(trip, 24);
        verify(boundaryProtectionService).performEmergencyCleanup(trip, 7L);
    }

    @Test
    void ordinaryRolesCannotInvokeAnyDirectOperation() throws Exception {
        for (User.Role role : List.of(User.Role.DRIVER, User.Role.SHIPPER, User.Role.FLEET_OWNER)) {
            var authentication = userAuthentication(role);
            for (String operation : List.of("/enforce", "/compress", "/archive", "/emergency-cleanup?confirm=true")) {
                mockMvc.perform(post(BASE_PATH + operation).with(authentication(authentication)))
                        .andExpect(status().isForbidden());
            }
        }

        verifyNoInteractions(tripService, boundaryProtectionService);
    }

    @Test
    void unauthenticatedCallersCannotInvokeAnyDirectOperation() throws Exception {
        for (String operation : List.of("/enforce", "/compress", "/archive", "/emergency-cleanup?confirm=true")) {
            mockMvc.perform(post(BASE_PATH + operation))
                    .andExpect(status().isUnauthorized());
        }

        verifyNoInteractions(tripService, boundaryProtectionService);
    }

    @Test
    void emergencyCleanupStillRequiresExplicitConfirmation() throws Exception {
        mockMvc.perform(post(BASE_PATH + "/emergency-cleanup?confirm=false")
                        .with(authentication(userAuthentication(User.Role.ADMIN))))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(tripService, boundaryProtectionService);
    }

    private UsernamePasswordAuthenticationToken userAuthentication(User.Role role) {
        User user = User.builder()
                .id(7L)
                .fullName("Test " + role)
                .phone("test-" + role)
                .password("unused")
                .role(role)
                .build();
        return UsernamePasswordAuthenticationToken.authenticated(
                user, null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
    }
}
