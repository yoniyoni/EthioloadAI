package com.ethioloadai.auth;

import com.ethioloadai.auth.dto.RegisterRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link RegisterRequest} validation.
 *
 * Covers:
 * - Fix 1: admin role is rejected at the DTO validation layer.
 * - Legitimate roles (shipper, driver, fleet_owner) are accepted.
 * - Phone-only registration (null email) passes validation.
 */
@DisplayName("RegisterRequest validation")
class RegisterRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    // ── Helper ──────────────────────────────────────────────────────────────

    private RegisterRequest validRequest(String role) {
        return new RegisterRequest("Abebe Girma", "+251911223344", null, "securePass1", role);
    }

    private Set<ConstraintViolation<RegisterRequest>> validate(RegisterRequest req) {
        return validator.validate(req);
    }

    // ── Fix 1: Admin role blocked ────────────────────────────────────────────

    @Test
    @DisplayName("role=admin produces a validation violation")
    void adminRoleIsRejected() {
        Set<ConstraintViolation<RegisterRequest>> violations = validate(validRequest("admin"));
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("role"));
    }

    // ── Legitimate roles accepted ────────────────────────────────────────────

    @ParameterizedTest(name = "role={0} is accepted")
    @ValueSource(strings = {"shipper", "driver", "fleet_owner"})
    @DisplayName("legitimate roles pass validation")
    void legitimateRolesAreAccepted(String role) {
        Set<ConstraintViolation<RegisterRequest>> violations = validate(validRequest(role));
        // No role-related violation expected
        assertThat(violations).noneMatch(v -> v.getPropertyPath().toString().equals("role"));
    }

    // ── Fix 2: Phone-only (null email) registration ──────────────────────────

    @Test
    @DisplayName("null email passes validation — drivers may register phone-only")
    void nullEmailPassesValidation() {
        RegisterRequest req = new RegisterRequest("Driver One", "+251922334455", null, "pass123", "driver");
        Set<ConstraintViolation<RegisterRequest>> violations = validate(req);
        assertThat(violations).noneMatch(v -> v.getPropertyPath().toString().equals("email"));
    }

    @Test
    @DisplayName("invalid email format produces a violation")
    void invalidEmailIsRejected() {
        RegisterRequest req = new RegisterRequest("Shipper One", "+251933445566", "not-an-email", "pass123", "shipper");
        Set<ConstraintViolation<RegisterRequest>> violations = validate(req);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("email"));
    }

    @Test
    @DisplayName("valid email passes validation")
    void validEmailPassesValidation() {
        RegisterRequest req = new RegisterRequest("Shipper Two", "+251944556677", "shipper@example.com", "pass123", "shipper");
        Set<ConstraintViolation<RegisterRequest>> violations = validate(req);
        assertThat(violations).noneMatch(v -> v.getPropertyPath().toString().equals("email"));
    }

    // ── Other field validations ──────────────────────────────────────────────

    @Test
    @DisplayName("blank fullName produces a violation")
    void blankFullNameIsRejected() {
        RegisterRequest req = new RegisterRequest("", "+251911223344", null, "pass123", "shipper");
        Set<ConstraintViolation<RegisterRequest>> violations = validate(req);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("fullName"));
    }

    @Test
    @DisplayName("password shorter than 6 characters produces a violation")
    void shortPasswordIsRejected() {
        RegisterRequest req = new RegisterRequest("Name", "+251911223344", null, "abc", "shipper");
        Set<ConstraintViolation<RegisterRequest>> violations = validate(req);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("password"));
    }

    @Test
    @DisplayName("invalid phone format produces a violation")
    void invalidPhoneIsRejected() {
        RegisterRequest req = new RegisterRequest("Name", "not-a-phone", null, "pass123", "shipper");
        Set<ConstraintViolation<RegisterRequest>> violations = validate(req);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("phone"));
    }
}
