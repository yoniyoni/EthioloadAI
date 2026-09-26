package com.ethioloadai.message.converter;

import com.ethioloadai.message.entity.MessageType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class MessageTypeConverter implements AttributeConverter<MessageType, String> {

    @Override
    public String convertToDatabaseColumn(MessageType attribute) {
        return attribute == null ? null : attribute.name().toLowerCase();
    }

    @Override
    public MessageType convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return null;
        try {
            return MessageType.valueOf(dbData.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid message type in database: '" + dbData + "'");
        }
    }
}
