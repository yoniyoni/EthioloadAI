package com.ethioloadai.vehicle.document.converter;

import com.ethioloadai.vehicle.document.entity.VehicleDocument;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Locale;

@Converter(autoApply = false)
public class VehicleDocumentTypeConverter implements AttributeConverter<VehicleDocument.DocumentType, String> {

    @Override
    public String convertToDatabaseColumn(VehicleDocument.DocumentType attribute) {
        return attribute == null ? null : attribute.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public VehicleDocument.DocumentType convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        try {
            return VehicleDocument.DocumentType.valueOf(dbData.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid vehicle document_type in database: '" + dbData + "'", e);
        }
    }
}
