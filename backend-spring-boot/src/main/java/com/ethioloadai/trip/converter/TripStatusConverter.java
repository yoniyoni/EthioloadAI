package com.ethioloadai.trip.converter;

import com.ethioloadai.trip.entity.Trip;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class TripStatusConverter implements AttributeConverter<Trip.TripStatus, String> {

    @Override
    public String convertToDatabaseColumn(Trip.TripStatus status) {
        return status == null ? null : status.name().toLowerCase();
    }

    @Override
    public Trip.TripStatus convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return null;
        try {
            return Trip.TripStatus.valueOf(dbData.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid trip_status in database: '" + dbData + "'");
        }
    }
}
