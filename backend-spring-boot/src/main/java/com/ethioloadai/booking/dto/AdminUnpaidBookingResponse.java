package com.ethioloadai.booking.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AdminUnpaidBookingResponse {

    private Long id;
    private BigDecimal estimatedPrice;
    private BigDecimal commissionFee;
    private String bookingStatus;
    private String route;
    private PartyInfo shipper;
    private PartyInfo driver;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    // ── Compatibility getters for legacy snake_case frontend consumers ───────

    @JsonProperty("estimated_price")
    public BigDecimal getEstimatedPriceSnake() {
        return estimatedPrice;
    }

    @JsonProperty("commission_fee")
    public BigDecimal getCommissionFeeSnake() {
        return commissionFee;
    }

    @JsonProperty("booking_status")
    public String getBookingStatusSnake() {
        return bookingStatus;
    }

    @JsonProperty("created_at")
    public LocalDateTime getCreatedAtSnake() {
        return createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PartyInfo {
        private String name;
        private String phone;
    }
}
