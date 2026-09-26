package com.ethioloadai.auth;

import com.ethioloadai.auth.controller.MobileAuthCompatibilityController;
import com.ethioloadai.auth.dto.*;
import com.ethioloadai.auth.service.AuthenticationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MobileAuthCompatibilityControllerTest {

    @Test
    @DisplayName("POST /api/login delegates to authenticationService.login")
    void testMobileLogin() {
        AuthenticationService service = mock(AuthenticationService.class);
        MobileAuthCompatibilityController controller = new MobileAuthCompatibilityController(service);

        LoginRequest req = new LoginRequest();
        req.setIdentifier("driver@test.com");
        req.setPassword("Password123!");

        AuthenticationResponse mockAuth = AuthenticationResponse.builder()
                .token("jwt-token")
                .user(UserResponse.builder().id(1L).email("driver@test.com").role("DRIVER").build())
                .build();

        when(service.login(req)).thenReturn(mockAuth);

        ResponseEntity<AuthenticationResponse> response = controller.login(req);
        assertEquals(200, response.getStatusCode().value());
        assertEquals("jwt-token", response.getBody().getToken());
    }

    @Test
    @DisplayName("POST /api/register delegates to authenticationService.register")
    void testMobileRegister() {
        AuthenticationService service = mock(AuthenticationService.class);
        MobileAuthCompatibilityController controller = new MobileAuthCompatibilityController(service);

        RegisterRequest req = new RegisterRequest();
        req.setEmail("shipper@test.com");
        req.setPassword("Password123!");
        req.setFullName("Abebe");
        req.setPhone("+251911223344");
        req.setRole("SHIPPER");

        AuthenticationResponse mockAuth = AuthenticationResponse.builder()
                .token("jwt-reg-token")
                .user(UserResponse.builder().id(2L).email("shipper@test.com").role("SHIPPER").build())
                .build();

        when(service.register(req)).thenReturn(mockAuth);

        ResponseEntity<AuthenticationResponse> response = controller.register(req);
        assertEquals(201, response.getStatusCode().value());
        assertEquals("jwt-reg-token", response.getBody().getToken());
    }
}
