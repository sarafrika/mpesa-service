package com.sarafrika.apps.mpesaservice.utils.converters;

import com.sarafrika.apps.mpesaservice.utils.enums.OutgoingPaymentStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class OutgoingPaymentStatusConverter implements AttributeConverter<OutgoingPaymentStatus, String> {

    @Override
    public String convertToDatabaseColumn(OutgoingPaymentStatus paymentStatus) {
        return paymentStatus != null ? paymentStatus.name() : null;
    }

    @Override
    public OutgoingPaymentStatus convertToEntityAttribute(String dbData) {
        return dbData != null ? OutgoingPaymentStatus.valueOf(dbData) : null;
    }
}