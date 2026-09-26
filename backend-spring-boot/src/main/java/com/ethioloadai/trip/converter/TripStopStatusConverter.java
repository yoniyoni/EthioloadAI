package com.ethioloadai.trip.converter;

import com.ethioloadai.trip.entity.TripStop;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class TripStopStatusConverter implements AttributeConverter<TripStop.StopStatus, String> {

    @Override
    public String convertToDatabaseColumn(TripStop.StopStatus status) {
        return status == null ? null : status.name().toLowerCase();
    }

    @Override
    public TripStop.StopStatus convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return null;
        try {
            return TripStop.StopStatus.valueOf(dbData.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid stop_status in database: '" + dbData + "'");
        }
    }
}
