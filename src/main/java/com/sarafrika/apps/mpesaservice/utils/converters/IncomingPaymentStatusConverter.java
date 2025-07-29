package com.sarafrika.apps.mpesaservice.utils.converters;

import com.sarafrika.apps.mpesaservice.utils.enums.IncomingPaymentStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class IncomingPaymentStatusConverter implements AttributeConverter<IncomingPaymentStatus, String> {

    @Override
    public String convertToDatabaseColumn(IncomingPaymentStatus paymentStatus) {
        return paymentStatus != null ? paymentStatus.name() : null;
    }

    @Override
    public IncomingPaymentStatus convertToEntityAttribute(String dbData) {
        return dbData != null ? IncomingPaymentStatus.valueOf(dbData) : null;
    }
}