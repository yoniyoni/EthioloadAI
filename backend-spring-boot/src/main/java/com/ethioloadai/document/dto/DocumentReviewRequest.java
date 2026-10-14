package com.ethioloadai.document.dto;

import com.ethioloadai.document.entity.DriverDocument;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Admin review decision.
 *
 * <p>Used for both approval and rejection. The reviewer and timestamp are
 * server-assigned from the authenticated principal.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentReviewRequest {

    @NotNull(message = "status is required")
    private DriverDocument.DocumentStatus status;

    /**
     * Rejection reason (required when status = REJECTED, ignored otherwise).
     */
    @JsonAlias({"rejection_reason", "rejectionReason"})
    private String rejectionReason;

    public void setStatus(Object statusObj) {
        if (statusObj == null) {
            this.status = null;
        } else if (statusObj instanceof DriverDocument.DocumentStatus s) {
            this.status = s;
        } else {
            String str = statusObj.toString().trim();
            if (str.equalsIgnoreCase("approve") || str.equalsIgnoreCase("approved")) {
                this.status = DriverDocument.DocumentStatus.APPROVED;
            } else if (str.equalsIgnoreCase("reject") || str.equalsIgnoreCase("rejected")) {
                this.status = DriverDocument.DocumentStatus.REJECTED;
            } else if (str.equalsIgnoreCase("pending")) {
                this.status = DriverDocument.DocumentStatus.PENDING;
            } else {
                try {
                    this.status = DriverDocument.DocumentStatus.valueOf(str.toUpperCase());
                } catch (IllegalArgumentException e) {
                    this.status = null;
                }
            }
        }
    }

    @JsonSetter("action")
    public void setAction(String action) {
        if (this.status == null && action != null) {
            setStatus(action);
        }
    }
}
