package com.ethioloadai.bid.converter;

import com.ethioloadai.bid.entity.Bid;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class BidStatusConverter implements AttributeConverter<Bid.BidStatus, String> {

    @Override
    public String convertToDatabaseColumn(Bid.BidStatus status) {
        return status == null ? null : status.name().toLowerCase();
    }

    @Override
    public Bid.BidStatus convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return null;
        try {
            return Bid.BidStatus.valueOf(dbData.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid bid status in database: '" + dbData + "'");
        }
    }
}
