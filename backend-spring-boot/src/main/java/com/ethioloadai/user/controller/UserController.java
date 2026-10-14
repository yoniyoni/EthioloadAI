package com.ethioloadai.user.controller;

import com.ethioloadai.user.dto.*;
import com.ethioloadai.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for user administration management.
 *
 * <h2>Endpoints</h2>
 * <pre>
 *   GET    /api/users            — list all users with optional role/pagination (ADMIN only)
 *   GET    /api/admin/users      — alias for user directory listing (ADMIN only)
 *   POST   /api/admin/users      — create new user account (ADMIN only)
 *   PUT    /api/admin/users/{id} — update user account (ADMIN only)
 *   DELETE /api/admin/users/{id} — delete user without history (ADMIN only)
 *   POST   /api/admin/drivers    — create driver/fleet owner account (ADMIN only)
 * </pre>
 */
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "User Administration", description = "Admin user directory and account management")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    private final UserService userService;

    @GetMapping({"/users", "/admin/users"})
    @Operation(summary = "List users (ADMIN only)",
               description = "Returns paginated list of users with optional role filter. Supports both page/size and limit/offset.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Users retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden — ADMIN role required")
    })
    public ResponseEntity<UserListResponse> listUsers(
            @Parameter(description = "Filter by role: shipper, driver, fleet_owner, admin")
            @RequestParam(required = false) String role,
            @Parameter(description = "Page number (0-based, default 0)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size (default 50)")
            @RequestParam(defaultValue = "50") int size,
            @Parameter(description = "Legacy limit parameter")
            @RequestParam(required = false) Integer limit,
            @Parameter(description = "Legacy offset parameter")
            @RequestParam(required = false) Integer offset) {

        int resolvedSize = (limit != null && limit > 0) ? limit : size;
        int resolvedPage = (offset != null && offset > 0 && resolvedSize > 0)
                ? (offset / resolvedSize)
                : page;

        log.debug("GET /api/users — role={}, page={}, size={}", role, resolvedPage, resolvedSize);
        return ResponseEntity.ok(userService.listUsers(role, resolvedPage, resolvedSize));
    }

    @PostMapping("/admin/users")
    @Operation(summary = "Create user (ADMIN only)",
               description = "Creates a new user account with specified role.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "User created successfully"),
        @ApiResponse(responseCode = "400", description = "Validation failed"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden — ADMIN role required"),
        @ApiResponse(responseCode = "409", description = "Email or phone already registered")
    })
    public ResponseEntity<Map<String, Object>> createUser(
            @Valid @RequestBody CreateUserRequest request) {

        log.info("POST /api/admin/users — email={}, role={}", request.getEmail(), request.getRole());
        UserResponse response = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("user", response));
    }

    @PutMapping("/admin/users/{id}")
    @Operation(summary = "Update user (ADMIN only)",
               description = "Updates user profile, role, password, and active status.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "User updated successfully"),
        @ApiResponse(responseCode = "400", description = "Validation failed"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden — ADMIN role required"),
        @ApiResponse(responseCode = "404", description = "User not found"),
        @ApiResponse(responseCode = "409", description = "Email or phone conflict")
    })
    public ResponseEntity<Map<String, Object>> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request) {

        log.info("PUT /api/admin/users/{} — updating fields", id);
        UserResponse response = userService.updateUser(id, request);
        return ResponseEntity.ok(Map.of("user", response));
    }

    @DeleteMapping("/admin/users/{id}")
    @Operation(summary = "Delete user (ADMIN only)",
               description = "Deletes a user account if no cargo or booking history exists.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "User deleted successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden — ADMIN role required"),
        @ApiResponse(responseCode = "404", description = "User not found"),
        @ApiResponse(responseCode = "409", description = "Cannot delete user with cargo or booking history")
    })
    public ResponseEntity<Map<String, String>> deleteUser(
            @PathVariable Long id) {

        log.info("DELETE /api/admin/users/{}", id);
        userService.deleteUser(id);
        return ResponseEntity.ok(Map.of("message", "User deleted successfully"));
    }

    @PostMapping("/admin/drivers")
    @Operation(summary = "Create driver or fleet owner (ADMIN only)",
               description = "Creates a driver account and returns user and driver envelope.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Driver created successfully"),
        @ApiResponse(responseCode = "400", description = "Validation failed"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden — ADMIN role required"),
        @ApiResponse(responseCode = "409", description = "Email or phone conflict")
    })
    public ResponseEntity<Map<String, Object>> createDriver(
            @Valid @RequestBody CreateDriverAdminRequest request) {

        log.info("POST /api/admin/drivers — email={}, role={}", request.getEmail(), request.getRole());
        Map<String, Object> response = userService.createDriver(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
