package com.ethioloadai.auth.service;

import com.ethioloadai.auth.dto.*;

public interface AuthenticationService {

    AuthenticationResponse register(RegisterRequest request);

    AuthenticationResponse login(LoginRequest request, String sourceIp);

    void logout(String token);

    UserResponse getCurrentUser(Long userId);

    void changePassword(Long userId, ChangePasswordRequest request);

    UserResponse updateProfile(Long userId, UpdateProfileRequest request);
}
