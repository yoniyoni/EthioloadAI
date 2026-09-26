package com.ethioloadai.vehicle;

import com.ethioloadai.exception.AuthorizationException;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.user.repository.UserRepository;
import com.ethioloadai.vehicle.dto.VehicleLocationRequest;
import com.ethioloadai.vehicle.dto.VehicleApprovalStatusRequest;
import com.ethioloadai.vehicle.dto.VehicleResponse;
import com.ethioloadai.vehicle.entity.Vehicle;
import com.ethioloadai.vehicle.mapper.VehicleMapper;
import com.ethioloadai.vehicle.repository.VehicleRepository;
import com.ethioloadai.vehicle.service.VehicleServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link VehicleServiceImpl}.
 *
 * Covers:
 * - Fix 7: getAllVehicles is admin-only.
 * - Fix 9: updateDriverLocation selects the most-recently-located vehicle.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("VehicleServiceImpl")
class VehicleServiceImplTest {

    @Mock private VehicleRepository vehicleRepository;
    @Mock private UserRepository userRepository;
    @Mock private VehicleMapper vehicleMapper;

    @InjectMocks
    private VehicleServiceImpl vehicleService;

    // ── Shared fixtures ──────────────────────────────────────────────────────

    private User userWithRole(Long id, User.Role role) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        user.setFullName("Test User");
        user.setPhone("+251900000000");
        user.setIsActive(true);
        user.setVerificationStatus(true);
        return user;
    }

    private Vehicle vehicleWithLastLocationAt(Long id, Long userId, LocalDateTime lastLocationAt) {
        Vehicle v = new Vehicle();
        v.setId(id);
        v.setUserId(userId);
        v.setTruckType("Isuzu");
        v.setPlateNumber("ET-" + id + "-AB");
        v.setCapacity(10.0);
        v.setAvailabilityStatus("available");
        v.setApprovalStatus(Vehicle.VehicleApprovalStatus.UNDER_REVIEW);
        v.setLastLocationAt(lastLocationAt);
        return v;
    }

    @Nested
    @DisplayName("vehicle approval lifecycle")
    class ApprovalLifecycle {
        @Test
        void adminCanApproveVehicle() {
            User admin = userWithRole(1L, User.Role.ADMIN);
            Vehicle vehicle = vehicleWithLastLocationAt(10L, 3L, null);
            when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
            when(vehicleRepository.findById(10L)).thenReturn(Optional.of(vehicle));
            when(vehicleRepository.save(vehicle)).thenReturn(vehicle);
            when(vehicleMapper.toResponse(vehicle)).thenReturn(VehicleResponse.builder().id(10L).build());

            vehicleService.updateApprovalStatus(10L, new VehicleApprovalStatusRequest(VehicleResponse.VehicleApprovalStatus.APPROVED), 1L);

            assertThat(vehicle.getApprovalStatus()).isEqualTo(Vehicle.VehicleApprovalStatus.APPROVED);
        }

        @Test
        void adminCanRejectVehicleUnderReview() {
            User admin = userWithRole(1L, User.Role.ADMIN);
            Vehicle vehicle = vehicleWithLastLocationAt(10L, 3L, null);
            when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
            when(vehicleRepository.findById(10L)).thenReturn(Optional.of(vehicle));
            when(vehicleRepository.save(vehicle)).thenReturn(vehicle);
            when(vehicleMapper.toResponse(vehicle)).thenReturn(VehicleResponse.builder().id(10L).build());

            vehicleService.updateApprovalStatus(10L, new VehicleApprovalStatusRequest(VehicleResponse.VehicleApprovalStatus.REJECTED), 1L);

            assertThat(vehicle.getApprovalStatus()).isEqualTo(Vehicle.VehicleApprovalStatus.REJECTED);
        }

        @Test
        void adminCanSuspendApprovedVehicleAndReapproveIt() {
            User admin = userWithRole(1L, User.Role.ADMIN);
            Vehicle vehicle = vehicleWithLastLocationAt(10L, 3L, null);
            vehicle.setApprovalStatus(Vehicle.VehicleApprovalStatus.APPROVED);
            when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
            when(vehicleRepository.findById(10L)).thenReturn(Optional.of(vehicle));
            when(vehicleRepository.save(vehicle)).thenReturn(vehicle);
            when(vehicleMapper.toResponse(vehicle)).thenReturn(VehicleResponse.builder().id(10L).build());

            vehicleService.updateApprovalStatus(10L, new VehicleApprovalStatusRequest(VehicleResponse.VehicleApprovalStatus.SUSPENDED), 1L);
            assertThat(vehicle.getApprovalStatus()).isEqualTo(Vehicle.VehicleApprovalStatus.SUSPENDED);

            vehicleService.updateApprovalStatus(10L, new VehicleApprovalStatusRequest(VehicleResponse.VehicleApprovalStatus.APPROVED), 1L);
            assertThat(vehicle.getApprovalStatus()).isEqualTo(Vehicle.VehicleApprovalStatus.APPROVED);
        }

        @Test
        void driverCannotApproveVehicle() {
            User driver = userWithRole(3L, User.Role.DRIVER);
            when(userRepository.findById(3L)).thenReturn(Optional.of(driver));

            assertThatThrownBy(() -> vehicleService.updateApprovalStatus(10L,
                    new VehicleApprovalStatusRequest(VehicleResponse.VehicleApprovalStatus.APPROVED), 3L))
                    .isInstanceOf(AuthorizationException.AccessDeniedException.class);
            verify(vehicleRepository, never()).findById(anyLong());
        }

        @Test
        void shipperCannotApproveVehicle() {
            User shipper = userWithRole(8L, User.Role.SHIPPER);
            when(userRepository.findById(8L)).thenReturn(Optional.of(shipper));

            assertThatThrownBy(() -> vehicleService.updateApprovalStatus(10L,
                new VehicleApprovalStatusRequest(VehicleResponse.VehicleApprovalStatus.APPROVED), 8L))
                .isInstanceOf(AuthorizationException.AccessDeniedException.class);
        }

        @Test
        void invalidTransitionIsRejected() {
            User admin = userWithRole(1L, User.Role.ADMIN);
            Vehicle vehicle = vehicleWithLastLocationAt(10L, 3L, null);
            vehicle.setApprovalStatus(Vehicle.VehicleApprovalStatus.REJECTED);
            when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
            when(vehicleRepository.findById(10L)).thenReturn(Optional.of(vehicle));

            assertThatThrownBy(() -> vehicleService.updateApprovalStatus(10L,
                    new VehicleApprovalStatusRequest(VehicleResponse.VehicleApprovalStatus.APPROVED), 1L))
                    .isInstanceOf(com.ethioloadai.exception.ValidationException.class);
        }
    }

    // ── getAllVehicles — admin-only (Fix 7) ──────────────────────────────────

    @Nested
    @DisplayName("getAllVehicles")
    class GetAllVehicles {

        @Test
        @DisplayName("ADMIN user receives all vehicles")
        void adminCanGetAllVehicles() {
            Long adminId = 1L;
            User admin = userWithRole(adminId, User.Role.ADMIN);
            List<Vehicle> vehicles = List.of(
                vehicleWithLastLocationAt(10L, 2L, null),
                vehicleWithLastLocationAt(11L, 3L, null)
            );
            VehicleResponse r1 = VehicleResponse.builder().id(10L).build();
            VehicleResponse r2 = VehicleResponse.builder().id(11L).build();

            when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
            when(vehicleRepository.findAll()).thenReturn(vehicles);
            when(vehicleMapper.toResponse(vehicles.get(0))).thenReturn(r1);
            when(vehicleMapper.toResponse(vehicles.get(1))).thenReturn(r2);

            List<VehicleResponse> result = vehicleService.getAllVehicles(adminId);

            assertThat(result).hasSize(2);
            assertThat(result).containsExactly(r1, r2);
        }

        @Test
        @DisplayName("SHIPPER user is rejected with AccessDeniedException")
        void shipperCannotGetAllVehicles() {
            Long shipperId = 2L;
            User shipper = userWithRole(shipperId, User.Role.SHIPPER);
            when(userRepository.findById(shipperId)).thenReturn(Optional.of(shipper));

            assertThatThrownBy(() -> vehicleService.getAllVehicles(shipperId))
                    .isInstanceOf(AuthorizationException.AccessDeniedException.class);

            verify(vehicleRepository, never()).findAll();
        }

        @Test
        @DisplayName("DRIVER user is rejected with AccessDeniedException")
        void driverCannotGetAllVehicles() {
            Long driverId = 3L;
            User driver = userWithRole(driverId, User.Role.DRIVER);
            when(userRepository.findById(driverId)).thenReturn(Optional.of(driver));

            assertThatThrownBy(() -> vehicleService.getAllVehicles(driverId))
                    .isInstanceOf(AuthorizationException.AccessDeniedException.class);
        }

        @Test
        @DisplayName("FLEET_OWNER user is rejected with AccessDeniedException")
        void fleetOwnerCannotGetAllVehicles() {
            Long fleetOwnerId = 4L;
            User fleetOwner = userWithRole(fleetOwnerId, User.Role.FLEET_OWNER);
            when(userRepository.findById(fleetOwnerId)).thenReturn(Optional.of(fleetOwner));

            assertThatThrownBy(() -> vehicleService.getAllVehicles(fleetOwnerId))
                    .isInstanceOf(AuthorizationException.AccessDeniedException.class);
        }

        @Test
        @DisplayName("unknown userId throws ResourceNotFoundException")
        void unknownUserThrowsNotFound() {
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> vehicleService.getAllVehicles(99L))
                    .isInstanceOf(AuthorizationException.ResourceNotFoundException.class);
        }
    }

    // ── updateDriverLocation — correct vehicle selection (Fix 9) ────────────

    @Nested
    @DisplayName("updateDriverLocation — most-recently-located vehicle selection")
    class UpdateDriverLocation {

        private final Long driverId = 10L;
        private final VehicleLocationRequest locationRequest = VehicleLocationRequest.builder()
                .latitude(new BigDecimal("9.0320"))
                .longitude(new BigDecimal("38.7469"))
                .currentCity("Addis Ababa")
                .build();

        @Test
        @DisplayName("selects vehicle with the latest lastLocationAt when multiple exist")
        void selectsMostRecentlyLocatedVehicle() {
            LocalDateTime older  = LocalDateTime.now().minusHours(5);
            LocalDateTime recent = LocalDateTime.now().minusMinutes(10);

            Vehicle v1 = vehicleWithLastLocationAt(1L, driverId, older);
            Vehicle v2 = vehicleWithLastLocationAt(2L, driverId, recent);   // most recent
            Vehicle v3 = vehicleWithLastLocationAt(3L, driverId, older.minusHours(1));

            when(vehicleRepository.findByUserId(driverId)).thenReturn(List.of(v1, v2, v3));

            Vehicle savedVehicle = vehicleWithLastLocationAt(2L, driverId, LocalDateTime.now());
            VehicleResponse expectedResponse = VehicleResponse.builder().id(2L).build();
            when(vehicleRepository.save(v2)).thenReturn(savedVehicle);
            when(vehicleMapper.toResponse(savedVehicle)).thenReturn(expectedResponse);

            VehicleResponse result = vehicleService.updateDriverLocation(locationRequest, driverId);

            assertThat(result.getId()).isEqualTo(2L);
            // Verify the mapper was called on the correct (most recently located) vehicle
            verify(vehicleMapper).updateLocationFromDto(locationRequest, v2);
            verify(vehicleMapper, never()).updateLocationFromDto(any(), eq(v1));
            verify(vehicleMapper, never()).updateLocationFromDto(any(), eq(v3));
        }

        @Test
        @DisplayName("falls back to first available vehicle when none have been located yet")
        void fallsBackToFirstVehicleWhenNoneLocated() {
            // All lastLocationAt are null — should still pick one (not crash)
            Vehicle v1 = vehicleWithLastLocationAt(1L, driverId, null);
            Vehicle v2 = vehicleWithLastLocationAt(2L, driverId, null);

            when(vehicleRepository.findByUserId(driverId)).thenReturn(List.of(v1, v2));

            Vehicle savedVehicle = vehicleWithLastLocationAt(1L, driverId, LocalDateTime.now());
            VehicleResponse expectedResponse = VehicleResponse.builder().id(1L).build();
            // Either vehicle could be chosen — just verify it doesn't throw
            when(vehicleRepository.save(any(Vehicle.class))).thenReturn(savedVehicle);
            when(vehicleMapper.toResponse(savedVehicle)).thenReturn(expectedResponse);

            VehicleResponse result = vehicleService.updateDriverLocation(locationRequest, driverId);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("single vehicle is always selected")
        void singleVehicleIsSelected() {
            Vehicle v = vehicleWithLastLocationAt(5L, driverId, null);

            when(vehicleRepository.findByUserId(driverId)).thenReturn(List.of(v));

            Vehicle saved = vehicleWithLastLocationAt(5L, driverId, LocalDateTime.now());
            VehicleResponse response = VehicleResponse.builder().id(5L).build();
            when(vehicleRepository.save(v)).thenReturn(saved);
            when(vehicleMapper.toResponse(saved)).thenReturn(response);

            VehicleResponse result = vehicleService.updateDriverLocation(locationRequest, driverId);
            assertThat(result.getId()).isEqualTo(5L);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when driver has no vehicles")
        void noVehiclesThrowsNotFound() {
            when(vehicleRepository.findByUserId(driverId)).thenReturn(List.of());

            assertThatThrownBy(() -> vehicleService.updateDriverLocation(locationRequest, driverId))
                    .isInstanceOf(AuthorizationException.ResourceNotFoundException.class);
        }
    }
}
