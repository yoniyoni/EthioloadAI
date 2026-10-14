package com.ethioloadai.booking.converter;

import com.ethioloadai.booking.entity.Booking;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class BookingStatusConverter implements AttributeConverter<Booking.BookingStatus, String> {

    @Override
    public String convertToDatabaseColumn(Booking.BookingStatus status) {
        return status == null ? null : status.name().toLowerCase();
    }

    @Override
    public Booking.BookingStatus convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return null;
        try {
            return Booking.BookingStatus.valueOf(dbData.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid booking status in database: '" + dbData + "'");
        }
    }
}
