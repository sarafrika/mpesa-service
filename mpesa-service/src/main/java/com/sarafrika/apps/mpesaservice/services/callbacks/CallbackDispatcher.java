package com.sarafrika.apps.mpesaservice.services.callbacks;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sarafrika.apps.mpesaservice.exceptions.ConflictException;
import com.sarafrika.apps.mpesaservice.services.callbacks.CallbackAcks.Processing;
import com.sarafrika.apps.mpesaservice.utils.enums.CallbackKind;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.MethodParameter;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** Runs a stored callback body through the same controller method Safaricom's request reached, so a replay
 * takes exactly the live path (and its idempotency checks) instead of a copy of it. */
@Component
public class CallbackDispatcher {

    private final RequestMappingHandlerMapping mapping;
    private final ObjectMapper objectMapper;

    public CallbackDispatcher(@Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mapping,
                              ObjectMapper objectMapper) {
        this.mapping = mapping;
        this.objectMapper = objectMapper;
    }

    Processing dispatch(String endpoint, String body) {
        HandlerMethod handler = find(endpoint)
                .orElseThrow(() -> new ConflictException("No callback handler answers " + endpoint + " any more"));
        MethodParameter[] parameters = handler.getMethodParameters();
        if (parameters.length != 1 || !parameters[0].hasParameterAnnotation(RequestBody.class)) {
            throw new ConflictException("The handler for " + endpoint + " cannot be replayed from its body alone");
        }
        JavaType type = objectMapper.getTypeFactory().constructType(parameters[0].getGenericParameterType());
        Object argument;
        try {
            argument = objectMapper.readValue(body, type);
        } catch (Exception e) {
            return Processing.failure("The stored body does not fit the handler: " + CallbackOutcome.describe(e));
        }
        HandlerMethod resolved = handler.createWithResolvedBean();
        CallbackOutcome.begin();
        try {
            Object result = resolved.getMethod().invoke(resolved.getBean(), argument);
            String reported = CallbackOutcome.end();
            int status = result instanceof ResponseEntity<?> entity ? entity.getStatusCode().value() : 200;
            Object ack = result instanceof ResponseEntity<?> entity ? entity.getBody() : result;
            JsonNode tree = ack == null ? null : objectMapper.valueToTree(ack);
            return CallbackAcks.read(CallbackKind.fromPath(endpoint), status, tree, reported);
        } catch (InvocationTargetException e) {
            CallbackOutcome.end();
            return Processing.failure(CallbackOutcome.describe(e.getCause() == null ? e : e.getCause()));
        } catch (IllegalAccessException e) {
            CallbackOutcome.end();
            throw new IllegalStateException("Callback handler for " + endpoint + " is not accessible", e);
        }
    }

    Optional<HandlerMethod> find(String endpoint) {
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : mapping.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = entry.getKey();
            boolean post = info.getMethodsCondition().getMethods().isEmpty()
                    || info.getMethodsCondition().getMethods().contains(RequestMethod.POST);
            if (post && info.getPatternValues().contains(endpoint)) {
                return Optional.of(entry.getValue());
            }
        }
        return Optional.empty();
    }
}
