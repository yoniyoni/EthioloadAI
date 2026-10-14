package com.ethioloadai.document.converter;

import com.ethioloadai.document.entity.DriverDocument;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class DocumentStatusConverter implements AttributeConverter<DriverDocument.DocumentStatus, String> {

    @Override
    public String convertToDatabaseColumn(DriverDocument.DocumentStatus status) {
        return status == null ? null : status.name().toLowerCase();
    }

    @Override
    public DriverDocument.DocumentStatus convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return null;
        try {
            return DriverDocument.DocumentStatus.valueOf(dbData.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid document status in database: '" + dbData + "'");
        }
    }
}
