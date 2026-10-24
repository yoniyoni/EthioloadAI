package com.ethioloadai.auth.service;

import com.ethioloadai.auth.dto.*;
import com.ethioloadai.auth.mapper.AuthMapper;
import com.ethioloadai.exception.AuthenticationException;
import com.ethioloadai.exception.AuthorizationException;
import com.ethioloadai.exception.RegistrationConflictException;
import com.ethioloadai.exception.ValidationException;
import com.ethioloadai.security.jwt.JwtService;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthenticationServiceImpl implements AuthenticationService {
    private static final Logger log = LoggerFactory.getLogger(AuthenticationServiceImpl.class);
    // Match a hash for unknown identifiers to reduce account-dependent response timing.
    static final String DUMMY_PASSWORD_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final UserRepository userRepository;
    private final AuthMapper authMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginAttemptRateLimiter loginAttemptRateLimiter;

    @Override
    @Transactional
    public AuthenticationResponse register(RegisterRequest request) {
        return registerWithSourceIp(request, null);
    }

    @Override
    @Transactional
    public AuthenticationResponse register(RegisterRequest request, String sourceIp) {
        return registerWithSourceIp(request, sourceIp);
    }

    private AuthenticationResponse registerWithSourceIp(RegisterRequest request, String sourceIp) {
        loginAttemptRateLimiter.checkRegistration(sourceIp, request.getEmail(), request.getPhone());
        log.info("User registration initiated");

        // Map request to entity
        User user = authMapper.toEntity(request);

        // Hash password
        user.setPassword(passwordEncoder.encode(request.getPassword()));

        // Let the database uniqueness constraints handle concurrent registrations too.
        try {
            user = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            log.warn("Registration could not be completed because the supplied details conflict with existing data");
            throw new RegistrationConflictException();
        }

        log.info("User registration successful - userId: {}, role: {}", user.getId(), user.getRole());

        // Generate token
        String token = jwtService.generateToken(user);

        // Map to response
        UserResponse userResponse = authMapper.toResponse(user);

        return new AuthenticationResponse(userResponse, token);
    }

    @Override
    @Transactional(readOnly = true)
    public AuthenticationResponse login(LoginRequest request, String sourceIp) {
        loginAttemptRateLimiter.checkSource(sourceIp);

        User user = userRepository.findByEmailOrPhone(request.getIdentifier())
                .orElse(null);
        Long accountId = user == null ? null : user.getId();
        try {
            loginAttemptRateLimiter.checkIdentifier(request.getIdentifier(), accountId);
        } catch (AuthenticationException.TooManyLoginAttemptsException exception) {
            loginAttemptRateLimiter.refundSource(sourceIp);
            throw exception;
        }

        if (user == null) {
            passwordEncoder.matches(request.getPassword(), DUMMY_PASSWORD_HASH);
            throw new AuthenticationException.InvalidCredentialsException();
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            log.warn("Login failed - invalid credentials");
            throw new AuthenticationException.InvalidCredentialsException();
        }

        boolean pendingDriver = user.getRole() == User.Role.DRIVER
                && !Boolean.TRUE.equals(user.getVerificationStatus());
        if (!Boolean.TRUE.equals(user.getIsActive()) && !pendingDriver) {
            log.warn("Login failed - invalid credentials");
            throw new AuthenticationException.InvalidCredentialsException();
        }

        loginAttemptRateLimiter.recordSuccess(request.getIdentifier(), accountId, sourceIp);
        log.info("Login successful - userId: {}, role: {}", user.getId(), user.getRole());

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
        jwtService.revokeToken(token);
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
