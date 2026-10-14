package com.ethioloadai.analytics.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AdminEscrowResponse {
    private EscrowMetricItem held;
    private EscrowMetricItem inTransit;
    private EscrowMetricItem released;
    private EscrowMetricItem disputed;
}
