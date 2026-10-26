package com.sarafrika.apps.mpesaservice.exceptions.handlers;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Turns a Bean Validation path (grants[0].shortcodeUuid) into the request JSON's names
// (grants[0].shortcode_uuid), following @JsonProperty and the mapper's naming strategy.
final class JsonFieldNames {

    private static final Pattern SEGMENT = Pattern.compile("([^\\[]*)((?:\\[[^]]*])*)");

    private final ObjectMapper mapper;

    JsonFieldNames(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    String resolve(Class<?> root, String path) {
        if (root == null || path == null || path.isBlank()) {
            return path;
        }
        JavaType type = mapper.constructType(root);
        StringBuilder out = new StringBuilder();
        for (String segment : path.split("\\.")) {
            Matcher m = SEGMENT.matcher(segment);
            if (!m.matches()) {
                return append(out, segment).toString();
            }
            String name = m.group(1);
            String brackets = m.group(2);
            BeanPropertyDefinition property = type == null ? null : find(type, name);
            append(out, (property != null ? property.getName() : name) + brackets);
            type = property == null ? null : property.getPrimaryType();
            if (type != null && !brackets.isEmpty() && type.getContentType() != null) {
                type = type.getContentType();
            }
        }
        return out.toString();
    }

    private BeanPropertyDefinition find(JavaType type, String name) {
        if (type.isContainerType() || type.isPrimitive() || type.getRawClass().getName().startsWith("java.")) {
            return null;
        }
        return mapper.getDeserializationConfig().introspect(type).findProperties().stream()
                .filter(p -> p.getInternalName().equals(name))
                .findFirst()
                .orElse(null);
    }

    private static StringBuilder append(StringBuilder out, String part) {
        return (out.isEmpty() ? out : out.append('.')).append(part);
    }
}
