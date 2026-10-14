package com.ethioloadai.user.service;

import com.ethioloadai.user.dto.*;

import java.util.Map;

/**
 * Service interface for user administration operations.
 */
public interface UserService {

    /**
     * List users with optional role filter and pagination.
     */
    UserListResponse listUsers(String role, int page, int size);

    /**
     * Create a new user account (admin capability).
     */
    UserResponse createUser(CreateUserRequest request);

    /**
     * Update an existing user account (admin capability).
     */
    UserResponse updateUser(Long id, UpdateUserRequest request);

    /**
     * Delete a user if no cargo or booking history exists (admin capability).
     */
    void deleteUser(Long id);

    /**
     * Create a driver or fleet owner account (admin capability).
     */
    Map<String, Object> createDriver(CreateDriverAdminRequest request);
}
