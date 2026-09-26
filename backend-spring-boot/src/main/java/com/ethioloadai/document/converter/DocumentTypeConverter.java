package com.ethioloadai.document.converter;

import com.ethioloadai.document.entity.DriverDocument;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class DocumentTypeConverter implements AttributeConverter<DriverDocument.DocumentType, String> {

    @Override
    public String convertToDatabaseColumn(DriverDocument.DocumentType type) {
        return type == null ? null : type.name().toLowerCase();
    }

    @Override
    public DriverDocument.DocumentType convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return null;
        try {
            return DriverDocument.DocumentType.valueOf(dbData.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid document_type in database: '" + dbData + "'");
        }
    }
}
