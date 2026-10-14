package com.ethioloadai.freight.converter;

import com.ethioloadai.freight.entity.Freight;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA converter for {@link Freight.FreightStatus}.
 *
 * <p>Persists enum values in lowercase to match the PostgreSQL CHECK constraint
 * ({@code status IN ('posted', 'matched', 'in_transit', 'delivered', 'completed', 'cancelled')})
 * defined in V3 migration.
 * Using {@link jakarta.persistence.EnumType#STRING} directly writes uppercase ('POSTED'),
 * which would violate the constraint.
 */
@Converter(autoApply = false)
public class FreightStatusConverter implements AttributeConverter<Freight.FreightStatus, String> {

    @Override
    public String convertToDatabaseColumn(Freight.FreightStatus status) {
        if (status == null) {
            return null;
        }
        // POSTED → 'posted', IN_TRANSIT → 'in_transit', etc.
        return status.name().toLowerCase();
    }

    @Override
    public Freight.FreightStatus convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        try {
            return Freight.FreightStatus.valueOf(dbData.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "Invalid freight status value in database: '" + dbData + "'."
            );
        }
    }
}
