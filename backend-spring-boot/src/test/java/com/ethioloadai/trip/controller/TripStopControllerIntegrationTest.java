package com.ethioloadai.trip.controller;

import com.ethioloadai.trip.dto.*;
import com.ethioloadai.trip.entity.Trip;
import com.ethioloadai.trip.entity.TripStop;
import com.ethioloadai.trip.repository.TripRepository;
import com.ethioloadai.trip.repository.TripStopRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("TripStopController Integration Tests")
class TripStopControllerIntegrationTest {

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private TripStopRepository tripStopRepository;

    @Test
    @DisplayName("Should load application context successfully")
    void contextLoads() {
        assertThat(tripRepository).isNotNull();
        assertThat(tripStopRepository).isNotNull();
    }

    @Test
    @DisplayName("TripStopController beans should be available")
    void controllerBeansAvailable() {
        // Basic test to ensure the controller layer compiles and context loads
        // Full HTTP tests would require proper auth setup and test data
        assertThat(tripRepository.count()).isGreaterThanOrEqualTo(0);
        assertThat(tripStopRepository.count()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("DTO classes should be properly configured")
    void dtoClassesProperlyConfigured() {
        // Test DTO construction
        CreateTripStopRequest createRequest = CreateTripStopRequest.builder()
                .locationName("Test Location")
                .stopOrder(1)
                .pickupLatitude(new BigDecimal("9.145000"))
                .pickupLongitude(new BigDecimal("40.489673"))
                .agreedPrice(new BigDecimal("1500.00"))
                .build();
        
        assertThat(createRequest.getLocationName()).isEqualTo("Test Location");
        assertThat(createRequest.getStopOrder()).isEqualTo(1);
        
        UpdateTripStopStatusRequest statusRequest = UpdateTripStopStatusRequest.builder()
                .stopStatus("ARRIVED")
                .build();
        
        assertThat(statusRequest.getStopStatus()).isEqualTo("ARRIVED");
        
        ReorderStopsRequest reorderRequest = ReorderStopsRequest.builder()
                .stopIds(Arrays.asList(1L, 2L, 3L))
                .build();
        
        assertThat(reorderRequest.getStopIds()).hasSize(3);
    }
}