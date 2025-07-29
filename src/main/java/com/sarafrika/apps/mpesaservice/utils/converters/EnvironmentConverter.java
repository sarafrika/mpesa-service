package com.sarafrika.apps.mpesaservice.utils.converters;

import com.sarafrika.apps.mpesaservice.utils.enums.Environment;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class EnvironmentConverter implements AttributeConverter<Environment, String> {

    @Override
    public String convertToDatabaseColumn(Environment environment) {
        return environment != null ? environment.name() : null;
    }

    @Override
    public Environment convertToEntityAttribute(String dbData) {
        return dbData != null ? Environment.valueOf(dbData) : null;
    }
}