package com.sarafrika.apps.mpesaservice.utils.converters;

import com.sarafrika.apps.mpesaservice.utils.enums.IncomingPaymentType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class IncomingPaymentTypeConverter implements AttributeConverter<IncomingPaymentType, String> {

    @Override
    public String convertToDatabaseColumn(IncomingPaymentType paymentType) {
        return paymentType != null ? paymentType.name() : null;
    }

    @Override
    public IncomingPaymentType convertToEntityAttribute(String dbData) {
        return dbData != null ? IncomingPaymentType.valueOf(dbData) : null;
    }
}