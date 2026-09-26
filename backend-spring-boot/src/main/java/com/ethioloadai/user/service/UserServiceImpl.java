package com.ethioloadai.user.service;

import com.ethioloadai.booking.repository.BookingRepository;
import com.ethioloadai.freight.repository.FreightRepository;
import com.ethioloadai.user.dto.*;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.user.exception.UserException;
import com.ethioloadai.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service implementation for managing users and administrative user operations.
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    private final UserRepository userRepository;
    private final FreightRepository freightRepository;
    private final BookingRepository bookingRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(readOnly = true)
    public UserListResponse listUsers(String role, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        int safePage = Math.max(page, 0);

        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<User> userPage;

        if (role != null && !role.isBlank()) {
            User.Role roleEnum = parseRole(role);
            userPage = userRepository.findByRole(roleEnum, pageable);
        } else {
            userPage = userRepository.findAll(pageable);
        }

        List<UserResponse> userList = userPage.getContent().stream()
                .map(UserResponse::from)
                .collect(Collectors.toList());

        return UserListResponse.builder()
                .users(userList)
                .total(userPage.getTotalElements())
                .build();
    }

    @Override
    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new UserException.UserAlreadyExistsException("Email already registered: " + request.getEmail());
        }
        if (userRepository.existsByPhone(request.getPhone())) {
            throw new UserException.UserAlreadyExistsException("Phone number already registered: " + request.getPhone());
        }

        User.Role role = parseRole(request.getRole());

        User user = User.builder()
                .fullName(request.getName().trim())
                .email(request.getEmail().trim())
                .phone(request.getPhone().trim())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(role)
                .verificationStatus(false)
                .isActive(true)
                .build();

        User saved = userRepository.save(user);
        log.info("Created new user id={} role={}", saved.getId(), saved.getRole());

        return UserResponse.from(saved);
    }

    @Override
    @Transactional
    public UserResponse updateUser(Long id, UpdateUserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserException.UserNotFoundException(id));

        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            String trimmedEmail = request.getEmail().trim();
            if (!trimmedEmail.equalsIgnoreCase(user.getEmail()) && userRepository.existsByEmailAndIdNot(trimmedEmail, id)) {
                throw new UserException.UserAlreadyExistsException("Email already in use: " + trimmedEmail);
            }
            user.setEmail(trimmedEmail);
        }

        if (request.getPhone() != null && !request.getPhone().isBlank()) {
            String trimmedPhone = request.getPhone().trim();
            if (!trimmedPhone.equals(user.getPhone()) && userRepository.existsByPhoneAndIdNot(trimmedPhone, id)) {
                throw new UserException.UserAlreadyExistsException("Phone number already in use: " + trimmedPhone);
            }
            user.setPhone(trimmedPhone);
        }

        if (request.getName() != null && !request.getName().isBlank()) {
            user.setFullName(request.getName().trim());
        }

        if (request.getRole() != null && !request.getRole().isBlank()) {
            user.setRole(parseRole(request.getRole()));
        }

        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            if (request.getPassword().length() < 6) {
                throw new UserException.UserValidationException("Password must be at least 6 characters");
            }
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        if (request.getIsActive() != null) {
            user.setIsActive(request.getIsActive());
        }

        User saved = userRepository.save(user);
        log.info("Updated user id={}", saved.getId());

        return UserResponse.from(saved);
    }

    @Override
    @Transactional
    public void deleteUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserException.UserNotFoundException(id));

        boolean hasCargoHistory = !freightRepository.findByShipper_Id(id).isEmpty();
        boolean hasBookingHistory = !bookingRepository.findByDriver_Id(id).isEmpty()
                || !bookingRepository.findByShipperId(id).isEmpty();

        if (hasCargoHistory || hasBookingHistory) {
            throw new UserException.UserHasHistoryException(
                    "Cannot delete user: they have cargo or booking history. Deactivate their account instead.");
        }

        userRepository.delete(user);
        log.info("Deleted user with id={}", id);
    }

    @Override
    @Transactional
    public Map<String, Object> createDriver(CreateDriverAdminRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new UserException.UserAlreadyExistsException("Email already registered: " + request.getEmail());
        }
        if (userRepository.existsByPhone(request.getPhone())) {
            throw new UserException.UserAlreadyExistsException("Phone number already registered: " + request.getPhone());
        }

        User.Role role = request.getRole() != null && !request.getRole().isBlank()
                ? parseRole(request.getRole())
                : User.Role.DRIVER;

        User user = User.builder()
                .fullName(request.getName().trim())
                .email(request.getEmail().trim())
                .phone(request.getPhone().trim())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(role)
                .verificationStatus(false)
                .isActive(true)
                .build();

        User saved = userRepository.save(user);
        log.info("Created driver/fleet user id={} role={}", saved.getId(), saved.getRole());

        UserResponse userResponse = UserResponse.from(saved);
        Map<String, Object> driverMap = new HashMap<>();
        driverMap.put("id", saved.getId());
        driverMap.put("userId", saved.getId());
        driverMap.put("fullName", saved.getFullName());
        driverMap.put("phone", saved.getPhone());
        driverMap.put("licenseNumber", request.getLicenseNumber());
        driverMap.put("nationalId", request.getNationalId());
        driverMap.put("yearsExperience", request.getYearsExperience() != null ? request.getYearsExperience() : 0);

        return Map.of("user", userResponse, "driver", driverMap);
    }

    private User.Role parseRole(String roleStr) {
        if (roleStr == null || roleStr.isBlank()) {
            return User.Role.SHIPPER;
        }
        try {
            return User.Role.valueOf(roleStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new UserException.UserValidationException(
                    "Invalid role: " + roleStr + ". Must be one of: shipper, driver, fleet_owner, admin");
        }
    }
}
