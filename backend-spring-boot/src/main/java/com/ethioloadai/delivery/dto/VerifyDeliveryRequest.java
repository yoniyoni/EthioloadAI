package com.ethioloadai.delivery.dto;

import jakarta.validation.constraints.Size;

/**
 * Request body for shipper to verify or reject a delivery.
 *
 * <p>Only {@code action} is required. {@code rejectionReason} is
 * required when {@code action=REJECT}.
 */
public record VerifyDeliveryRequest(
        /** "VERIFY" or "REJECT". */
        String action,

        @Size(max = 1000)
        String rejectionReason
) {}
