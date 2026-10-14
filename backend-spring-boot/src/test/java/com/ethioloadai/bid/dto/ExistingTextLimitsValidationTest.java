package com.ethioloadai.bid.dto;

import com.ethioloadai.message.dto.CreateMessageRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ExistingTextLimitsValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void messageContentOver2000CharactersRemainsRejected() {
        CreateMessageRequest request = new CreateMessageRequest(1L, null, "M".repeat(2001), "text");

        assertThat(validator.validate(request))
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("content"));
    }

    @Test
    void bidNoteOver1000CharactersRemainsRejected() {
        CreateBidRequest request = CreateBidRequest.builder()
                .vehicleId(1L)
                .amount(new BigDecimal("1.00"))
                .note("N".repeat(1001))
                .build();

        assertThat(validator.validate(request))
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("note"));
    }

    @Test
    void counterNoteOver500CharactersRemainsRejected() {
        CounterOfferRequest request = CounterOfferRequest.builder()
                .counterAmount(new BigDecimal("1.00"))
                .counterNote("N".repeat(501))
                .build();

        assertThat(validator.validate(request))
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("counterNote"));
    }
}
