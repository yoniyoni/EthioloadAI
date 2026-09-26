package com.ethioloadai.carrier.dispatch.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Phase 6 — Request body for a driver rejection (optional rejection reason).
 *
 * <p>Used by the driver-reject endpoint. Acceptance has no request body.
 */
@Getter
@Setter
@NoArgsConstructor
public class DriverRespondRequest {

    /** Optional reason the driver is rejecting this assignment. */
    private String rejectionReason;
}
