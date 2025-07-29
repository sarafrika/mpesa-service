package com.sarafrika.apps.mpesaservice.utils.converters;

import com.sarafrika.apps.mpesaservice.utils.enums.ShortcodeType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ShortcodeTypeConverter implements AttributeConverter<ShortcodeType, String> {

    @Override
    public String convertToDatabaseColumn(ShortcodeType shortcodeType) {
        return shortcodeType != null ? shortcodeType.name() : null;
    }

    @Override
    public ShortcodeType convertToEntityAttribute(String dbData) {
        return dbData != null ? ShortcodeType.valueOf(dbData) : null;
    }
}