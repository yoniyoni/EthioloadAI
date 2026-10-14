package com.ethioloadai.booking.dto;

import com.ethioloadai.payment.dto.PaymentResponse;
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
public class CashPaymentSettlementResponse {

    private String message;
    private PaymentResponse payment;
}
