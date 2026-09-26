package com.ethioloadai.freight.converter;

import com.ethioloadai.freight.entity.Freight;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class FreightServiceTypeConverter implements AttributeConverter<Freight.ServiceType, String> {

    @Override
    public String convertToDatabaseColumn(Freight.ServiceType type) {
        return type == null ? null : type.name().toLowerCase();
    }

    @Override
    public Freight.ServiceType convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return null;
        try {
            return Freight.ServiceType.valueOf(dbData.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid service_type in database: '" + dbData + "'");
        }
    }
}
