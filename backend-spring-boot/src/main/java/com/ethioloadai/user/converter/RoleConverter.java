package com.ethioloadai.user.converter;

import com.ethioloadai.user.entity.User;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class RoleConverter implements AttributeConverter<User.Role, String> {

    @Override
    public String convertToDatabaseColumn(User.Role role) {
        if (role == null) {
            return null;
        }
        return role.name().toLowerCase();
    }

    @Override
    public User.Role convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return null;
        }
        try {
            return User.Role.valueOf(dbData.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid role value in database: " + dbData);
        }
    }
}
