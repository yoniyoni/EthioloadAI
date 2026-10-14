package com.ethioloadai.dispute;

import com.ethioloadai.dispute.controller.DisputeController;
import com.ethioloadai.dispute.dto.CreateDisputeRequest;
import com.ethioloadai.dispute.dto.DisputeListResponse;
import com.ethioloadai.dispute.dto.DisputeResponse;
import com.ethioloadai.dispute.dto.ResolveDisputeRequest;
import com.ethioloadai.dispute.service.DisputeService;
import com.ethioloadai.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DisputeController")
class DisputeControllerTest {

    @Mock
    private DisputeService disputeService;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private DisputeController disputeController;

    private User caller;

    @BeforeEach
    void setUp() {
        caller = User.builder()
                .id(1L)
                .fullName("Abebe Shipper")
                .role(User.Role.SHIPPER)
                .build();
    }

    @Test
    @DisplayName("GET /api/disputes?freightId={id} returns 200 OK with DisputeListResponse")
    void getDisputesByFreightId_returns200Ok() {
        // JwtAuthenticationFilter stores the raw User entity as principal — not UserDetailsImpl
        when(authentication.getPrincipal()).thenReturn(caller);

        DisputeResponse dispute = new DisputeResponse(
                100L, 10L, 20L, 1L,
                "Damaged goods", "Boxes broken",
                "open", null, null, null,
                LocalDateTime.now(), LocalDateTime.now(), null
        );
        DisputeListResponse listResponse = DisputeListResponse.of(List.of(dispute), 1);
        when(disputeService.getDisputesByFreightId(10L, caller)).thenReturn(listResponse);

        ResponseEntity<DisputeListResponse> response = disputeController.getDisputes(10L, authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().total()).isEqualTo(1);
        assertThat(response.getBody().disputes().get(0).reason()).isEqualTo("Damaged goods");
        verify(disputeService).getDisputesByFreightId(10L, caller);
    }

    @Test
    @DisplayName("GET /api/disputes (all) returns 200 OK for admin")
    void getAllDisputes_returns200Ok() {
        User adminUser = User.builder().id(99L).role(User.Role.ADMIN).build();
        when(authentication.getPrincipal()).thenReturn(adminUser);

        DisputeListResponse listResponse = DisputeListResponse.of(List.of(), 0);
        when(disputeService.getAllDisputes(adminUser)).thenReturn(listResponse);

        ResponseEntity<DisputeListResponse> response = disputeController.getDisputes(null, authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().total()).isZero();
        verify(disputeService).getAllDisputes(adminUser);
    }

    @Test
    @DisplayName("POST /api/disputes returns 201 Created with DisputeResponse")
    void fileDispute_returns201Created() {
        when(authentication.getPrincipal()).thenReturn(caller);

        CreateDisputeRequest request = new CreateDisputeRequest(10L, "Late cargo", "24hr delay");
        DisputeResponse created = new DisputeResponse(
                105L, 10L, null, 1L,
                "Late cargo", "24hr delay",
                "open", null, null, null,
                LocalDateTime.now(), LocalDateTime.now(), null
        );
        when(disputeService.createDispute(request, caller)).thenReturn(created);

        ResponseEntity<DisputeResponse> response = disputeController.fileDispute(request, authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().id()).isEqualTo(105L);
        assertThat(response.getBody().status()).isEqualTo("open");
        verify(disputeService).createDispute(request, caller);
    }

    @Test
    @DisplayName("PATCH /api/disputes/{id}/resolve returns 200 OK with resolved DisputeResponse")
    void resolveDispute_returns200Ok() {
        User adminUser = User.builder().id(99L).role(User.Role.ADMIN).build();
        when(authentication.getPrincipal()).thenReturn(adminUser);

        ResolveDisputeRequest request = new ResolveDisputeRequest("refund", "Full refund approved", new BigDecimal("10000.00"));
        DisputeResponse resolved = new DisputeResponse(
                105L, 10L, null, 1L,
                "Late cargo", "24hr delay",
                "resolved", "refund", "Full refund approved", new BigDecimal("10000.00"),
                LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now()
        );
        when(disputeService.resolveDispute(105L, request, adminUser)).thenReturn(resolved);

        ResponseEntity<DisputeResponse> response = disputeController.resolveDispute(105L, request, authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("resolved");
        assertThat(response.getBody().resolution()).isEqualTo("refund");
        assertThat(response.getBody().refundAmount()).isEqualByComparingTo("10000.00");
        verify(disputeService).resolveDispute(105L, request, adminUser);
    }
}
