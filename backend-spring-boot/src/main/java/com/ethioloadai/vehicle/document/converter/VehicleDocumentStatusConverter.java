package com.ethioloadai.vehicle.document.converter;

import com.ethioloadai.vehicle.document.entity.VehicleDocument;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Locale;

@Converter(autoApply = false)
public class VehicleDocumentStatusConverter implements AttributeConverter<VehicleDocument.DocumentStatus, String> {

    @Override
    public String convertToDatabaseColumn(VehicleDocument.DocumentStatus attribute) {
        return attribute == null ? null : attribute.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public VehicleDocument.DocumentStatus convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        try {
            return VehicleDocument.DocumentStatus.valueOf(dbData.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid vehicle document status in database: '" + dbData + "'", e);
        }
    }
}
