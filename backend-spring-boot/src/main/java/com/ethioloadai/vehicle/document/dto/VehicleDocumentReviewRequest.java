package com.ethioloadai.vehicle.document.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VehicleDocumentReviewRequest {
    @JsonAlias({"action"})
    @JsonProperty("status")
    private String status;

    @JsonProperty("rejectionReason")
    @JsonAlias({"rejection_reason"})
    private String rejectionReason;
}
