package com.ethioloadai.freight.converter;

import com.ethioloadai.freight.entity.Freight;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class FreightPriceTypeConverter implements AttributeConverter<Freight.PriceType, String> {

    @Override
    public String convertToDatabaseColumn(Freight.PriceType type) {
        return type == null ? null : type.name().toLowerCase();
    }

    @Override
    public Freight.PriceType convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return null;
        try {
            return Freight.PriceType.valueOf(dbData.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid price_type in database: '" + dbData + "'");
        }
    }
}
