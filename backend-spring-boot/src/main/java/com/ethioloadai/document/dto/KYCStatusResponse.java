package com.ethioloadai.document.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * KYC completeness status for a driver.
 *
 * <p>Used to inform the driver/admin which documents are missing/pending/approved.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KYCStatusResponse {

    private Long    driverId;
    private boolean isActive;
    private boolean kycComplete;
    private int     approvedCount;
    private int     requiredCount;
    
    /**
     * Document type → status mapping.
     * Example: {"LICENSE": "APPROVED", "NATIONAL_ID": "PENDING", "INSURANCE": "MISSING"}
     */
    private Map<String, String> documentStatuses;
}
