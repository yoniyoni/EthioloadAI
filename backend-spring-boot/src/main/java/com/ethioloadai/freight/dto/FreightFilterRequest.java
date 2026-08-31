package com.ethioloadai.freight.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FreightFilterRequest {
    private String status;
    private String materialType;
    private String pickupLocation;
    private String destination;
    private BigDecimal minWeight;
    private BigDecimal maxWeight;
    private BigDecimal minBudget;
    private BigDecimal maxBudget;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate deadlineAfter;
    private Integer page;
    private Integer size;
}
