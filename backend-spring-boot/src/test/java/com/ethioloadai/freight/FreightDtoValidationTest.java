package com.ethioloadai.freight;

import com.ethioloadai.freight.dto.CreateFreightRequest;
import com.ethioloadai.freight.dto.UpdateFreightRequest;
import com.ethioloadai.freight.entity.Freight;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for DTO validation annotations to ensure V6 field validation works correctly.
 * Covers size limits, pattern validation, and decimal constraints.
 */
@DisplayName("Freight DTO Validation")
class FreightDtoValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @ParameterizedTest
    @ValueSource(ints = {4999, 5000, 5001})
    void createCargoDescriptionEnforces5000CharacterMaximum(int length) {
        CreateFreightRequest request = CreateFreightRequest.builder()
                .cargoDescription("A".repeat(length))
                .build();

        boolean hasCargoDescriptionViolation = validator.validate(request).stream()
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("cargoDescription"));
        assertThat(hasCargoDescriptionViolation).isEqualTo(length > 5000);
    }

    @ParameterizedTest
    @ValueSource(ints = {4999, 5000, 5001})
    void updateCargoDescriptionEnforces5000CharacterMaximum(int length) {
        UpdateFreightRequest request = UpdateFreightRequest.builder()
                .cargoDescription("A".repeat(length))
                .build();

        boolean hasCargoDescriptionViolation = validator.validate(request).stream()
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("cargoDescription"));
        assertThat(hasCargoDescriptionViolation).isEqualTo(length > 5000);
    }

    @Nested
    @DisplayName("CreateFreightRequest Validation")
    class CreateFreightRequestValidation {

        @Test
        @DisplayName("Valid intracity request passes validation")
        void validIntracityRequest() {
            CreateFreightRequest request = CreateFreightRequest.builder()
                    .serviceType(Freight.ServiceType.INTRACITY)
                    .priceType(Freight.PriceType.NEGOTIABLE)
                    .city("Addis Ababa")
                    .pickupArea("Bole District")
                    .dropoffArea("Piazza Area")
                    .preferredDate(LocalDate.now().plusDays(1))
                    .itemsDescription("Office furniture and equipment")
                    .vehicleTypeNeeded("pickup truck")
                    .bidDeadline(LocalDateTime.now().plusDays(1))
                    .build();

            Set<ConstraintViolation<CreateFreightRequest>> violations = validator.validate(request);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("Valid intercity request passes validation")
        void validIntercityRequest() {
            CreateFreightRequest request = CreateFreightRequest.builder()
                    .serviceType(Freight.ServiceType.INTERCITY)
                    .priceType(Freight.PriceType.FIXED)
                    .pickupLocation("Addis Ababa")
                    .destination("Hawassa")
                    .materialType("grain")
                    .weightTons(new BigDecimal("10.5"))
                    .budget(new BigDecimal("5000.00"))
                    .urgencyLevel("normal")
                    .build();

            Set<ConstraintViolation<CreateFreightRequest>> violations = validator.validate(request);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("City exceeding max length fails validation")
        void cityTooLong() {
            CreateFreightRequest request = CreateFreightRequest.builder()
                    .city("A".repeat(256)) // Exceeds 255 character limit
                    .build();

            Set<ConstraintViolation<CreateFreightRequest>> violations = validator.validate(request);
            assertThat(violations).isNotEmpty();
            assertThat(violations.iterator().next().getMessage())
                    .contains("City name must not exceed 255 characters");
        }

        @Test
        @DisplayName("Pickup area exceeding max length fails validation")
        void pickupAreaTooLong() {
            CreateFreightRequest request = CreateFreightRequest.builder()
                    .pickupArea("A".repeat(256)) // Exceeds 255 character limit
                    .build();

            Set<ConstraintViolation<CreateFreightRequest>> violations = validator.validate(request);
            assertThat(violations).isNotEmpty();
            assertThat(violations.iterator().next().getMessage())
                    .contains("Pickup area must not exceed 255 characters");
        }

        @Test
        @DisplayName("Items description exceeding max length fails validation")
        void itemsDescriptionTooLong() {
            CreateFreightRequest request = CreateFreightRequest.builder()
                    .itemsDescription("A".repeat(2001)) // Exceeds 2000 character limit
                    .build();

            Set<ConstraintViolation<CreateFreightRequest>> violations = validator.validate(request);
            assertThat(violations).isNotEmpty();
            assertThat(violations.iterator().next().getMessage())
                    .contains("Items description must not exceed 2000 characters");
        }

        @Test
        @DisplayName("Invalid material type fails validation")
        void invalidMaterialType() {
            CreateFreightRequest request = CreateFreightRequest.builder()
                    .materialType("invalid_type")
                    .build();

            Set<ConstraintViolation<CreateFreightRequest>> violations = validator.validate(request);
            assertThat(violations).isNotEmpty();
            // The pattern validation message should contain the allowed values
            String message = violations.iterator().next().getMessage();
            assertThat(message).contains("Material type must be one of");
        }

        @Test
        @DisplayName("Invalid urgency level fails validation")
        void invalidUrgencyLevel() {
            CreateFreightRequest request = CreateFreightRequest.builder()
                    .urgencyLevel("super_urgent")
                    .build();

            Set<ConstraintViolation<CreateFreightRequest>> violations = validator.validate(request);
            assertThat(violations).isNotEmpty();
            String message = violations.iterator().next().getMessage();
            assertThat(message).contains("Urgency level must be one of");
        }

        @Test
        @DisplayName("Invalid latitude fails validation")
        void invalidLatitude() {
            CreateFreightRequest request = CreateFreightRequest.builder()
                    .pickupLatitude(new BigDecimal("91.0")) // > 90
                    .build();

            Set<ConstraintViolation<CreateFreightRequest>> violations = validator.validate(request);
            assertThat(violations).isNotEmpty();
            assertThat(violations.iterator().next().getMessage())
                    .contains("Latitude must be between -90 and 90");
        }

        @Test
        @DisplayName("Invalid longitude fails validation")
        void invalidLongitude() {
            CreateFreightRequest request = CreateFreightRequest.builder()
                    .pickupLongitude(new BigDecimal("181.0")) // > 180
                    .build();

            Set<ConstraintViolation<CreateFreightRequest>> violations = validator.validate(request);
            assertThat(violations).isNotEmpty();
            assertThat(violations.iterator().next().getMessage())
                    .contains("Longitude must be between -180 and 180");
        }

        @Test
        @DisplayName("Invalid weight fails validation")
        void invalidWeight() {
            CreateFreightRequest request = CreateFreightRequest.builder()
                    .weightTons(new BigDecimal("0.05")) // < 0.1 minimum
                    .build();

            Set<ConstraintViolation<CreateFreightRequest>> violations = validator.validate(request);
            assertThat(violations).isNotEmpty();
            assertThat(violations.iterator().next().getMessage())
                    .contains("Weight must be at least 0.1 tons");
        }

        @Test
        @DisplayName("Invalid budget fails validation")
        void invalidBudget() {
            CreateFreightRequest request = CreateFreightRequest.builder()
                    .budget(new BigDecimal("0.50")) // < 1.0 minimum
                    .build();

            Set<ConstraintViolation<CreateFreightRequest>> violations = validator.validate(request);
            assertThat(violations).isNotEmpty();
            assertThat(violations.iterator().next().getMessage())
                    .contains("Budget must be at least 1 ETB");
        }
    }

    @Nested
    @DisplayName("UpdateFreightRequest Validation")
    class UpdateFreightRequestValidation {

        @Test
        @DisplayName("Valid update request passes validation")
        void validUpdateRequest() {
            UpdateFreightRequest request = UpdateFreightRequest.builder()
                    .budget(new BigDecimal("6000.00"))
                    .urgencyLevel("high")
                    .priceType(Freight.PriceType.FIXED)
                    .city("Dire Dawa")
                    .pickupArea("Commercial District")
                    .dropoffArea("Residential Area")
                    .preferredDate(LocalDate.now().plusDays(2))
                    .itemsDescription("Updated items description")
                    .vehicleTypeNeeded("box truck")
                    .bidDeadline(LocalDateTime.now().plusDays(2))
                    .build();

            Set<ConstraintViolation<UpdateFreightRequest>> violations = validator.validate(request);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("Invalid budget in update fails validation")
        void invalidBudgetUpdate() {
            UpdateFreightRequest request = UpdateFreightRequest.builder()
                    .budget(new BigDecimal("0.50")) // < 1.0 minimum
                    .build();

            Set<ConstraintViolation<UpdateFreightRequest>> violations = validator.validate(request);
            assertThat(violations).isNotEmpty();
            assertThat(violations.iterator().next().getMessage())
                    .contains("Budget must be at least 1 ETB");
        }

        @Test
        @DisplayName("City exceeding max length in update fails validation")
        void cityTooLongUpdate() {
            UpdateFreightRequest request = UpdateFreightRequest.builder()
                    .city("A".repeat(256)) // Exceeds 255 character limit
                    .build();

            Set<ConstraintViolation<UpdateFreightRequest>> violations = validator.validate(request);
            assertThat(violations).isNotEmpty();
            assertThat(violations.iterator().next().getMessage())
                    .contains("City name must not exceed 255 characters");
        }

        @Test
        @DisplayName("Items description exceeding max length in update fails validation")
        void itemsDescriptionTooLongUpdate() {
            UpdateFreightRequest request = UpdateFreightRequest.builder()
                    .itemsDescription("A".repeat(2001)) // Exceeds 2000 character limit
                    .build();

            Set<ConstraintViolation<UpdateFreightRequest>> violations = validator.validate(request);
            assertThat(violations).isNotEmpty();
            assertThat(violations.iterator().next().getMessage())
                    .contains("Items description must not exceed 2000 characters");
        }

        @Test
        @DisplayName("Invalid urgency level in update fails validation")
        void invalidUrgencyLevelUpdate() {
            UpdateFreightRequest request = UpdateFreightRequest.builder()
                    .urgencyLevel("extreme")
                    .build();

            Set<ConstraintViolation<UpdateFreightRequest>> violations = validator.validate(request);
            assertThat(violations).isNotEmpty();
            String message = violations.iterator().next().getMessage();
            assertThat(message).contains("Urgency level must be one of");
        }
    }
}