package com.ethioloadai.payment.converter;

import com.ethioloadai.payment.entity.Payment;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class PaymentStatusConverter implements AttributeConverter<Payment.PaymentStatus, String> {

    @Override
    public String convertToDatabaseColumn(Payment.PaymentStatus status) {
        return status == null ? null : status.name().toLowerCase();
    }

    @Override
    public Payment.PaymentStatus convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return null;
        try {
            return Payment.PaymentStatus.valueOf(dbData.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid payment_status in database: '" + dbData + "'");
        }
    }
}
