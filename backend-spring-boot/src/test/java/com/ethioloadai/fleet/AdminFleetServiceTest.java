package com.ethioloadai.fleet;

import com.ethioloadai.booking.repository.BookingRepository;
import com.ethioloadai.fleet.dto.AdminFleetOwnerResponse;
import com.ethioloadai.fleet.exception.FleetException;
import com.ethioloadai.fleet.service.FleetServiceImpl;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.user.repository.UserRepository;
import com.ethioloadai.vehicle.entity.Vehicle;
import com.ethioloadai.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Admin Fleet Service (B16)")
class AdminFleetServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private VehicleRepository vehicleRepository;
    @Mock private BookingRepository bookingRepository;

    private FleetServiceImpl service;

    private User adminUser;
    private User driverUser;
    private User fleetOwner1;
    private User fleetOwner2;
    private User driver1;
    private Vehicle vehicle1;

    @BeforeEach
    void setUp() {
        service = new FleetServiceImpl(userRepository, vehicleRepository, bookingRepository);

        adminUser = User.builder()
                .id(1L)
                .fullName("System Admin")
                .email("admin@ethioloadai.com")
                .role(User.Role.ADMIN)
                .build();

        driverUser = User.builder()
                .id(2L)
                .fullName("Test Driver")
                .role(User.Role.DRIVER)
                .build();

        fleetOwner1 = User.builder()
                .id(10L)
                .fullName("Abebe Fleet")
                .email("abebe@fleet.et")
                .phone("+251911000001")
                .role(User.Role.FLEET_OWNER)
                .verificationStatus(true)
                .isActive(true)
                .createdAt(LocalDateTime.now().minusDays(10))
                .updatedAt(LocalDateTime.now())
                .build();

        fleetOwner2 = User.builder()
                .id(20L)
                .fullName("Kebede Transport")
                .email("kebede@fleet.et")
                .phone("+251911000002")
                .role(User.Role.FLEET_OWNER)
                .verificationStatus(false)
                .isActive(true)
                .createdAt(LocalDateTime.now().minusDays(5))
                .updatedAt(LocalDateTime.now())
                .build();

        driver1 = User.builder()
                .id(101L)
                .fullName("Dawit Driver")
                .phone("+251911222333")
                .role(User.Role.DRIVER)
                .fleetOwnerId(10L)
                .verificationStatus(true)
                .build();

        vehicle1 = Vehicle.builder()
                .id(201L)
                .fleetOwnerId(10L)
                .plateNumber("3-12345-ET")
                .truckType("dry_van")
                .availabilityStatus("available")
                .build();
    }

    @Test
    @DisplayName("getAllFleetOwners returns mapped owners with batch drivers and vehicles")
    void getAllFleetOwners_asAdmin_returnsMappedOwnersWithBatchDriversAndVehicles() {
        when(userRepository.findByRoleOrderByCreatedAtDesc(User.Role.FLEET_OWNER))
                .thenReturn(List.of(fleetOwner1, fleetOwner2));

        when(userRepository.findByFleetOwnerIdIn(any()))
                .thenReturn(List.of(driver1));

        when(vehicleRepository.findByFleetOwnerIdIn(any()))
                .thenReturn(List.of(vehicle1));

        List<AdminFleetOwnerResponse> results = service.getAllFleetOwners(adminUser);

        assertThat(results).hasSize(2);

        // Owner 1 verification
        AdminFleetOwnerResponse res1 = results.get(0);
        assertThat(res1.getId()).isEqualTo(10L);
        assertThat(res1.getName()).isEqualTo("Abebe Fleet");
        assertThat(res1.getEmail()).isEqualTo("abebe@fleet.et");
        assertThat(res1.getPhone()).isEqualTo("+251911000001");
        assertThat(res1.getRole()).isEqualTo("fleet_owner");
        assertThat(res1.getIsVerified()).isTrue();
        assertThat(res1.getIsActive()).isTrue();
        assertThat(res1.getDriverCount()).isEqualTo(1);
        assertThat(res1.getVehicleCount()).isEqualTo(1);
        assertThat(res1.getDrivers()).hasSize(1);
        assertThat(res1.getDrivers().get(0).getId()).isEqualTo(101L);
        assertThat(res1.getDrivers().get(0).getName()).isEqualTo("Dawit Driver");
        assertThat(res1.getDrivers().get(0).getPhone()).isEqualTo("+251911222333");
        assertThat(res1.getDrivers().get(0).getVerified()).isTrue();
        assertThat(res1.getVehicles()).hasSize(1);
        assertThat(res1.getVehicles().get(0).getId()).isEqualTo(201L);
        assertThat(res1.getVehicles().get(0).getPlateNumber()).isEqualTo("3-12345-ET");
        assertThat(res1.getVehicles().get(0).getTruckType()).isEqualTo("dry_van");
        assertThat(res1.getVehicles().get(0).getStatus()).isEqualTo("available");

        // Owner 2 verification (empty drivers/vehicles)
        AdminFleetOwnerResponse res2 = results.get(1);
        assertThat(res2.getId()).isEqualTo(20L);
        assertThat(res2.getName()).isEqualTo("Kebede Transport");
        assertThat(res2.getDriverCount()).isEqualTo(0);
        assertThat(res2.getVehicleCount()).isEqualTo(0);
        assertThat(res2.getDrivers()).isEmpty();
        assertThat(res2.getVehicles()).isEmpty();

        // Verify batch queries were used (no N+1)
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> userCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(userRepository, times(1)).findByFleetOwnerIdIn(userCaptor.capture());
        assertThat(userCaptor.getValue()).containsExactlyInAnyOrder(10L, 20L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> vehicleCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(vehicleRepository, times(1)).findByFleetOwnerIdIn(vehicleCaptor.capture());
        assertThat(vehicleCaptor.getValue()).containsExactlyInAnyOrder(10L, 20L);
    }

    @Test
    @DisplayName("getAllFleetOwners returns empty list when no fleet owners exist")
    void getAllFleetOwners_emptyList_returnsEmptyList() {
        when(userRepository.findByRoleOrderByCreatedAtDesc(User.Role.FLEET_OWNER))
                .thenReturn(Collections.emptyList());

        List<AdminFleetOwnerResponse> results = service.getAllFleetOwners(adminUser);

        assertThat(results).isEmpty();
        verify(userRepository, never()).findByFleetOwnerIdIn(any());
        verify(vehicleRepository, never()).findByFleetOwnerIdIn(any());
    }

    @Test
    @DisplayName("getAllFleetOwners throws FleetAuthorizationException for non-admin")
    void getAllFleetOwners_nonAdmin_throwsException() {
        assertThatThrownBy(() -> service.getAllFleetOwners(driverUser))
                .isInstanceOf(FleetException.FleetAuthorizationException.class)
                .hasMessageContaining("Admin authorization required");

        verify(userRepository, never()).findByRoleOrderByCreatedAtDesc(any());
    }

    @Test
    @DisplayName("getAllFleetOwners throws FleetAuthorizationException for null user")
    void getAllFleetOwners_nullUser_throwsException() {
        assertThatThrownBy(() -> service.getAllFleetOwners(null))
                .isInstanceOf(FleetException.FleetAuthorizationException.class)
                .hasMessageContaining("Admin authorization required");

        verify(userRepository, never()).findByRoleOrderByCreatedAtDesc(any());
    }
}
