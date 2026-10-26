package com.sarafrika.apps.mpesaservice.utils.converters;

import com.sarafrika.apps.mpesaservice.utils.enums.GrantOperation;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/** Stores a set of grant operations as "B2C_PAYOUT,STK_PUSH" (declaration order, no spaces). */
@Converter
public class GrantOperationsConverter implements AttributeConverter<Set<GrantOperation>, String> {

    @Override
    public String convertToDatabaseColumn(Set<GrantOperation> operations) {
        if (operations == null || operations.isEmpty()) {
            return "";
        }
        return EnumSet.copyOf(operations).stream().map(Enum::name).collect(Collectors.joining(","));
    }

    @Override
    public Set<GrantOperation> convertToEntityAttribute(String column) {
        EnumSet<GrantOperation> operations = EnumSet.noneOf(GrantOperation.class);
        if (column != null && !column.isBlank()) {
            Arrays.stream(column.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                    .map(GrantOperation::valueOf).forEach(operations::add);
        }
        return operations;
    }
}
