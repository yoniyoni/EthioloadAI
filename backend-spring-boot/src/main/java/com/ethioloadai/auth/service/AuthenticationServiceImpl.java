package com.ethioloadai.auth.service;

import com.ethioloadai.auth.dto.*;
import com.ethioloadai.auth.mapper.AuthMapper;
import com.ethioloadai.exception.AuthenticationException;
import com.ethioloadai.exception.AuthorizationException;
import com.ethioloadai.exception.ValidationException;
import com.ethioloadai.security.jwt.JwtService;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthenticationServiceImpl implements AuthenticationService {

    private final UserRepository userRepository;
    private final AuthMapper authMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Override
    @Transactional
    public AuthenticationResponse register(RegisterRequest request) {
        log.info("User registration initiated - phone: {}, email: {}", request.getPhone(), request.getEmail());

        // Check phone uniqueness
        if (userRepository.existsByPhone(request.getPhone())) {
            log.warn("Registration failed - phone already exists: {}", request.getPhone());
            throw new ValidationException("The phone has already been taken.", "phone", "The phone has already been taken.");
        }

        // Check email uniqueness if provided
        if (request.getEmail() != null && userRepository.existsByEmail(request.getEmail())) {
            log.warn("Registration failed - email already exists: {}", request.getEmail());
            throw new ValidationException("The email has already been taken.", "email", "The email has already been taken.");
        }

        // Map request to entity
        User user = authMapper.toEntity(request);

        // Hash password
        user.setPassword(passwordEncoder.encode(request.getPassword()));

        // Save user
        user = userRepository.save(user);

        log.info("User registration successful - userId: {}, role: {}, phone: {}", user.getId(), user.getRole(), user.getPhone());

        // Generate token
        String token = jwtService.generateToken(user);

        // Map to response
        UserResponse userResponse = authMapper.toResponse(user);

        return new AuthenticationResponse(userResponse, token);
    }

    @Override
    @Transactional(readOnly = true)
    public AuthenticationResponse login(LoginRequest request) {
        log.info("Login attempt initiated - identifier: {}", request.getIdentifier());

        // Find user by email or phone
        User user = userRepository.findByEmailOrPhone(request.getIdentifier())
                .orElseThrow(() -> {
                    log.warn("Login failed - user not found for identifier: {}", request.getIdentifier());
                    return new AuthenticationException.InvalidCredentialsException();
                });

        // Verify password
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            log.warn("Login failed - invalid credentials for userId: {}, identifier: {}", user.getId(), request.getIdentifier());
            throw new AuthenticationException.InvalidCredentialsException();
        }

        // Check if account is active
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            log.warn("Login failed - account inactive for userId: {}, identifier: {}", user.getId(), request.getIdentifier());
            throw new AuthenticationException.AccountInactiveException();
        }

        // Check if driver is verified
        if (user.getRole() == User.Role.DRIVER && !Boolean.TRUE.equals(user.getVerificationStatus())) {
            log.warn("Login failed - driver not verified for userId: {}, identifier: {}", user.getId(), request.getIdentifier());
            throw new AuthenticationException.DriverNotVerifiedException();
        }

        log.info("Login successful - userId: {}, role: {}, identifier: {}", user.getId(), user.getRole(), request.getIdentifier());

        // Generate token
        String token = jwtService.generateToken(user);

        // Map to response
        UserResponse userResponse = authMapper.toResponse(user);

        return new AuthenticationResponse(userResponse, token);
    }

    @Override
    @Transactional
    public void logout(String token) {
        log.info("User logout");
        // Token revocation will be implemented with refresh tokens
        // For now, this is a placeholder
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(Long userId) {
        log.info("Fetching current user with ID: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthorizationException.ResourceNotFoundException("User"));

        return authMapper.toResponse(user);
    }

    @Override
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        log.info("Password change initiated - userId: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.warn("Password change failed - user not found: userId: {}", userId);
                    return new AuthorizationException.ResourceNotFoundException("User");
                });

        // Verify current password
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            log.warn("Password change failed - current password incorrect: userId: {}", userId);
            throw new AuthenticationException.CurrentPasswordIncorrectException();
        }

        // Validate new password is different from current password
        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            log.warn("Password change failed - new password same as current: userId: {}", userId);
            throw new ValidationException("New password must be different from current password.", "new_password", "New password must be different from current password.");
        }

        // Hash new password
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));

        // Save user
        userRepository.save(user);

        log.info("Password change successful - userId: {}", userId);
    }

    @Override
    @Transactional
    public UserResponse updateProfile(Long userId, UpdateProfileRequest request) {
        log.info("Updating profile for user ID: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthorizationException.ResourceNotFoundException("User"));

        // Handle name -> fullName mapping
        if (request.getName() != null && request.getFullName() == null) {
            request.setFullName(request.getName());
        }

        // Check phone uniqueness if changed
        if (request.getPhone() != null && !request.getPhone().equals(user.getPhone())) {
            if (userRepository.existsByPhoneAndIdNot(request.getPhone(), userId)) {
                throw new ValidationException("The phone has already been taken.", "phone", "The phone has already been taken.");
            }
        }

        // Map request to entity
        authMapper.updateEntityFromDto(request, user);

        // Save user
        user = userRepository.save(user);

        log.info("Profile updated successfully for user ID: {}", userId);

        return authMapper.toResponse(user);
    }
}
