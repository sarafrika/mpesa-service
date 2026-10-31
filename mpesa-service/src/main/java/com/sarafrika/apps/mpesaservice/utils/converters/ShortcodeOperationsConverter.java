package com.sarafrika.apps.mpesaservice.utils.converters;

import com.sarafrika.apps.mpesaservice.utils.enums.ShortcodeOperation;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/** Stores a shortcode's operations as "C2B,STK_PUSH" (declaration order, no spaces). */
@Converter
public class ShortcodeOperationsConverter implements AttributeConverter<Set<ShortcodeOperation>, String> {

    @Override
    public String convertToDatabaseColumn(Set<ShortcodeOperation> operations) {
        if (operations == null) {
            return null;
        }
        if (operations.isEmpty()) {
            return "";
        }
        return EnumSet.copyOf(operations).stream().map(Enum::name).collect(Collectors.joining(","));
    }

    @Override
    public Set<ShortcodeOperation> convertToEntityAttribute(String column) {
        if (column == null) {
            return null;
        }
        EnumSet<ShortcodeOperation> operations = EnumSet.noneOf(ShortcodeOperation.class);
        Arrays.stream(column.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .map(ShortcodeOperation::valueOf).forEach(operations::add);
        return operations;
    }
}
