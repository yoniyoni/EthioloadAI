package com.ethioloadai.auth;

import com.ethioloadai.auth.controller.MobileAuthCompatibilityController;
import com.ethioloadai.auth.controller.AuthController;
import com.ethioloadai.auth.dto.*;
import com.ethioloadai.auth.service.AuthenticationService;
import jakarta.servlet.http.HttpServletRequest;
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
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        when(servletRequest.getRemoteAddr()).thenReturn("192.0.2.1");

        LoginRequest req = new LoginRequest();
        req.setIdentifier("driver@test.com");
        req.setPassword("Password123!");

        AuthenticationResponse mockAuth = AuthenticationResponse.builder()
                .token("jwt-token")
                .user(UserResponse.builder().id(1L).email("driver@test.com").role("DRIVER").build())
                .build();

        when(service.login(req, "192.0.2.1")).thenReturn(mockAuth);

        ResponseEntity<AuthenticationResponse> response = controller.login(req, servletRequest);
        assertEquals(200, response.getStatusCode().value());
        assertEquals("jwt-token", response.getBody().getToken());
    }

    @Test
    @DisplayName("POST /api/auth/login passes the source address to the shared authentication service")
    void testPrimaryLoginPassesSourceAddress() {
        AuthenticationService service = mock(AuthenticationService.class);
        AuthController controller = new AuthController(service);
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        when(servletRequest.getRemoteAddr()).thenReturn("192.0.2.2");
        LoginRequest request = new LoginRequest("driver@test.com", "Password123!");
        AuthenticationResponse expected = AuthenticationResponse.builder()
                .token("jwt-token")
                .user(UserResponse.builder().id(1L).role("DRIVER").build())
                .build();
        when(service.login(request, "192.0.2.2")).thenReturn(expected);

        ResponseEntity<AuthenticationResponse> response = controller.login(request, servletRequest);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("jwt-token", response.getBody().getToken());
        verify(service).login(request, "192.0.2.2");
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
