package com.sarafrika.apps.mpesaservice.utils.converters;

import com.sarafrika.apps.mpesaservice.utils.enums.OutgoingPaymentType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class OutgoingPaymentTypeConverter implements AttributeConverter<OutgoingPaymentType, String> {

    @Override
    public String convertToDatabaseColumn(OutgoingPaymentType paymentType) {
        return paymentType != null ? paymentType.name() : null;
    }

    @Override
    public OutgoingPaymentType convertToEntityAttribute(String dbData) {
        return dbData != null ? OutgoingPaymentType.valueOf(dbData) : null;
    }
}