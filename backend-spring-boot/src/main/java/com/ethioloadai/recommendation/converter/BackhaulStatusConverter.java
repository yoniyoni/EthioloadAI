package com.ethioloadai.recommendation.converter;

import com.ethioloadai.recommendation.entity.BackhaulRecommendation;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class BackhaulStatusConverter implements AttributeConverter<BackhaulRecommendation.BackhaulStatus, String> {

    @Override
    public String convertToDatabaseColumn(BackhaulRecommendation.BackhaulStatus status) {
        return status == null ? null : status.name().toLowerCase();
    }

    @Override
    public BackhaulRecommendation.BackhaulStatus convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return null;
        try {
            return BackhaulRecommendation.BackhaulStatus.valueOf(dbData.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid backhaul status in database: '" + dbData + "'");
        }
    }
}
