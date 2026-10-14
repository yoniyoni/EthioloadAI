package com.ethioloadai.vehicle.converter;

import com.ethioloadai.vehicle.entity.Vehicle;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA converter for {@link Vehicle.VehicleCategory}.
 *
 * <p>Persists enum values in lowercase to match the PostgreSQL CHECK constraint
 * ({@code vehicle_category IN ('heavy', 'light')}) defined in V2 migration.
 * Using {@link jakarta.persistence.EnumType#STRING} directly would write the
 * Java enum name in uppercase ('HEAVY', 'LIGHT'), violating the constraint.
 */
@Converter(autoApply = false)
public class VehicleCategoryConverter implements AttributeConverter<Vehicle.VehicleCategory, String> {

    @Override
    public String convertToDatabaseColumn(Vehicle.VehicleCategory category) {
        if (category == null) {
            return null;
        }
        return category.name().toLowerCase(); // HEAVY → 'heavy', LIGHT → 'light'
    }

    @Override
    public Vehicle.VehicleCategory convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        try {
            return Vehicle.VehicleCategory.valueOf(dbData.toUpperCase()); // 'heavy' → HEAVY
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "Invalid vehicle_category value in database: '" + dbData + "'. Expected 'heavy' or 'light'."
            );
        }
    }
}
