package com.ethioloadai.fleet;

import com.ethioloadai.fleet.controller.AdminFleetController;
import com.ethioloadai.fleet.dto.AdminFleetOwnerResponse;
import com.ethioloadai.fleet.dto.AdminFleetOwnersListResponse;
import com.ethioloadai.fleet.service.FleetService;
import com.ethioloadai.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminFleetController (B16)")
class AdminFleetControllerTest {

    @Mock
    private FleetService fleetService;

    @InjectMocks
    private AdminFleetController controller;

    @Test
    @DisplayName("GET /api/admin/fleet-owners returns 200 OK with AdminFleetOwnersListResponse")
    void getAllFleetOwners_returns200Ok() {
        User adminUser = User.builder()
                .id(1L)
                .fullName("System Admin")
                .role(User.Role.ADMIN)
                .build();

        AdminFleetOwnerResponse mockOwner = AdminFleetOwnerResponse.builder()
                .id(10L)
                .name("Abebe Fleet")
                .email("abebe@fleet.et")
                .phone("+251911000001")
                .role("fleet_owner")
                .isVerified(true)
                .isActive(true)
                .driverCount(2)
                .vehicleCount(3)
                .drivers(List.of())
                .vehicles(List.of())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(fleetService.getAllFleetOwners(adminUser)).thenReturn(List.of(mockOwner));

        ResponseEntity<AdminFleetOwnersListResponse> response = controller.getAllFleetOwners(adminUser);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getFleetOwners()).hasSize(1);
        assertThat(response.getBody().getTotal()).isEqualTo(1);
        assertThat(response.getBody().getFleetOwners().get(0).getName()).isEqualTo("Abebe Fleet");
        assertThat(response.getBody().getFleetOwners().get(0).getDriverCount()).isEqualTo(2);
        assertThat(response.getBody().getFleetOwners().get(0).getVehicleCount()).isEqualTo(3);
    }
}
