package com.ethioloadai.bid.converter;

import com.ethioloadai.bid.entity.Bid;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class BidCounterByConverter implements AttributeConverter<Bid.CounterBy, String> {

    @Override
    public String convertToDatabaseColumn(Bid.CounterBy counterBy) {
        return counterBy == null ? null : counterBy.name().toLowerCase();
    }

    @Override
    public Bid.CounterBy convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return null;
        try {
            return Bid.CounterBy.valueOf(dbData.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid counter_by value in database: '" + dbData + "'");
        }
    }
}
