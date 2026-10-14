package com.ethioloadai.trip.converter;

import com.ethioloadai.trip.entity.Trip;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class TripTypeConverter implements AttributeConverter<Trip.TripType, String> {

    @Override
    public String convertToDatabaseColumn(Trip.TripType type) {
        return type == null ? null : type.name().toLowerCase();
    }

    @Override
    public Trip.TripType convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return null;
        try {
            return Trip.TripType.valueOf(dbData.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid trip_type in database: '" + dbData + "'");
        }
    }
}
