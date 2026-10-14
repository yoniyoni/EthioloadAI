package com.ethioloadai.carrier.offer.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Phase 5 — Request body for a counter-offer on an active CarrierOffer.
 *
 * <p>Used by both:
 * <ul>
 *   <li>Shipper countering a carrier's offer.</li>
 *   <li>Carrier countering a shipper's counter.</li>
 * </ul>
 *
 * <p>The server enforces turn-order: the acting party must match {@code nextActor}
 * on the parent {@link com.ethioloadai.carrier.offer.entity.CarrierOffer}.
 */
public record CounterOfferRequest(

    /**
     * The counter-proposed price in ETB.
     * Must be > 0 with at most 2 decimal places.
     */
    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.01", message = "amount must be greater than 0")
    @Digits(integer = 13, fraction = 2, message = "amount must have at most 13 integer digits and 2 decimal places")
    BigDecimal amount,

    /** Optional note explaining the counter. */
    String note
) {}
