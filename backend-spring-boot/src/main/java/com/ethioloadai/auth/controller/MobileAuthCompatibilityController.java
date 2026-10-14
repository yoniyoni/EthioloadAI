package com.ethioloadai.auth.controller;

import com.ethioloadai.auth.dto.*;
import com.ethioloadai.auth.service.AuthenticationService;
import com.ethioloadai.security.model.UserDetailsImpl;
import com.ethioloadai.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Compatibility controller providing root-level authentication aliases for the Flutter mobile application.
 * With server.servlet.context-path=/api the actual URLs become /api/login, /api/register, /api/me.
 * This matches the Flutter client which sends to baseUrl=http://host/api with paths /login, /register, /me.
 */
@RestController
@RequestMapping("")
@RequiredArgsConstructor
@Tag(name = "Mobile Auth Compatibility", description = "Authentication aliases for mobile clients")
public class MobileAuthCompatibilityController {
    private static final Logger log = LoggerFactory.getLogger(MobileAuthCompatibilityController.class);

    private final AuthenticationService authenticationService;

    @PostMapping("/login")
    @Operation(summary = "Mobile login alias")
    public ResponseEntity<AuthenticationResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest) {
        log.info("POST /api/login (mobile alias) - Login request received");
        AuthenticationResponse response = authenticationService.login(request, servletRequest.getRemoteAddr());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/register")
    @Operation(summary = "Mobile register alias")
    public ResponseEntity<AuthenticationResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.info("POST /api/register (mobile alias) - Registration request received");
        AuthenticationResponse response = authenticationService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/logout")
    @Operation(summary = "Mobile logout alias")
    public ResponseEntity<Map<String, Object>> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        log.info("POST /api/logout (mobile alias) - Logout request received");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            String token = authorization.substring(7);
            authenticationService.logout(token);
        }
        return ResponseEntity.ok(Map.of("success", true, "message", "Logged out successfully"));
    }

    @GetMapping("/me")
    @Operation(summary = "Mobile me alias")
    public ResponseEntity<UserResponse> getCurrentUser(Authentication authentication) {
        log.info("GET /api/me (mobile alias) - Get current user");
        if (authentication != null && authentication.getPrincipal() instanceof User user) {
            Long userId = user.getId();
            return ResponseEntity.ok(authenticationService.getCurrentUser(userId));
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    @PatchMapping("/me/password")
    @Operation(summary = "Mobile change password alias")
    public ResponseEntity<Map<String, Object>> changePassword(@Valid @RequestBody ChangePasswordRequest request, Authentication authentication) {
        log.info("PATCH /api/me/password (mobile alias) - Change password");
        if (authentication != null && authentication.getPrincipal() instanceof User user) {
            Long userId = user.getId();
            authenticationService.changePassword(userId, request);
            return ResponseEntity.ok(Map.of("success", true, "message", "Password changed successfully."));
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    @PatchMapping("/me")
    @Operation(summary = "Mobile update profile alias")
    public ResponseEntity<UserResponse> updateProfile(@Valid @RequestBody UpdateProfileRequest request, Authentication authentication) {
        log.info("PATCH /api/me (mobile alias) - Update profile");
        if (authentication != null && authentication.getPrincipal() instanceof User user) {
            Long userId = user.getId();
            return ResponseEntity.ok(authenticationService.updateProfile(userId, request));
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
}
