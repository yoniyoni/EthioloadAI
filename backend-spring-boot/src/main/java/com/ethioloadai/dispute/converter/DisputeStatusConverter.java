package com.ethioloadai.dispute.converter;

import com.ethioloadai.dispute.entity.DisputeStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class DisputeStatusConverter implements AttributeConverter<DisputeStatus, String> {

    @Override
    public String convertToDatabaseColumn(DisputeStatus attribute) {
        return attribute == null ? null : attribute.getValue();
    }

    @Override
    public DisputeStatus convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        return DisputeStatus.fromString(dbData);
    }
}
