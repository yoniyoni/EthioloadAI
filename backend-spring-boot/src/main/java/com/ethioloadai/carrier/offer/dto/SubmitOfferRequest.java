package com.ethioloadai.carrier.offer.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Phase 5 — Request body for a carrier submitting an initial offer on freight.
 *
 * <p>Security invariants (all enforced server-side, not in this DTO):
 * <ul>
 *   <li>{@code carrierCompanyId} is derived from the authenticated JWT + membership.</li>
 *   <li>{@code submittedBy} is set to the authenticated user ID.</li>
 *   <li>{@code pricingType} is taken from the freight entity, not from the client.</li>
 * </ul>
 *
 * <p>For FIXED-price freight the server ignores the submitted amount and uses
 * the freight's {@code budget} field instead.
 */
public record SubmitOfferRequest(

    /**
     * The carrier's proposed price in ETB.
     * For FIXED-price freight this is validated to equal the freight budget.
     * For NEGOTIABLE freight this is the opening offer amount.
     * Must be > 0 with at most 2 decimal places.
     */
    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.01", message = "amount must be greater than 0")
    @Digits(integer = 13, fraction = 2, message = "amount must have at most 13 integer digits and 2 decimal places")
    BigDecimal amount,

    /** Optional note from the carrier. */
    String note
) {}
