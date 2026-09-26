package com.ethioloadai.contract.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ContractResponse(
    Long id,
    Long freightId,
    String status,
    String paymentStatus,
    BigDecimal agreedPrice,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    LocalDate deadline,
    ContractPartyDto shipper,
    ContractPartyDto driver,
    ContractFreightDto freight
) {}
