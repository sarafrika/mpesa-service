package com.sarafrika.apps.mpesaservice.exceptions.handlers;

import com.sarafrika.apps.mpesaservice.dtos.admin.ApiError;
import com.sarafrika.apps.mpesaservice.exceptions.ConflictException;
import com.sarafrika.apps.mpesaservice.exceptions.InvalidRequestException;
import com.sarafrika.apps.mpesaservice.exceptions.NotFoundException;
import com.sarafrika.apps.mpesaservice.exceptions.UpstreamException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

/** Every admin endpoint fails with the same {@link ApiError} body. */
@RestControllerAdvice(basePackages = "com.sarafrika.apps.mpesaservice.controllers.admin")
@Slf4j
public class AdminExceptionHandler {

    private static final String INVALID = "The request is invalid";

    private final JsonFieldNames jsonNames;

    public AdminExceptionHandler(ObjectMapper mapper) {
        this.jsonNames = new JsonFieldNames(mapper);
    }

    /** Field errors name the request JSON's fields (business_name), not the Java properties. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> invalidBody(MethodArgumentNotValidException ex) {
        Class<?> body = ex.getParameter().getParameterType();
        List<ApiError.FieldError> fields = ex.getBindingResult().getAllErrors().stream()
                .map(error -> new ApiError.FieldError(
                        error instanceof FieldError field ? jsonNames.resolve(body, field.getField())
                                : error.getObjectName(),
                        error.getDefaultMessage()))
                .toList();
        return respond(ApiError.of(HttpStatus.BAD_REQUEST, INVALID, fields));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiError> invalidParameters(HandlerMethodValidationException ex) {
        List<ApiError.FieldError> fields = ex.getParameterValidationResults().stream()
                .flatMap(result -> {
                    if (result instanceof ParameterErrors errors && errors.hasFieldErrors()) {
                        Class<?> type = result.getMethodParameter().getParameterType();
                        return errors.getFieldErrors().stream().map(field -> new ApiError.FieldError(
                                jsonNames.resolve(type, field.getField()), field.getDefaultMessage()));
                    }
                    RequestParam param = result.getMethodParameter().getParameterAnnotation(RequestParam.class);
                    String name = param != null && !param.name().isBlank() ? param.name()
                            : result.getMethodParameter().getParameterName();
                    return result.getResolvableErrors().stream()
                            .map(MessageSourceResolvable::getDefaultMessage)
                            .map(message -> new ApiError.FieldError(name, message));
                })
                .toList();
        return respond(ApiError.of(HttpStatus.BAD_REQUEST, INVALID, fields));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> constraintViolation(ConstraintViolationException ex) {
        List<ApiError.FieldError> fields = ex.getConstraintViolations().stream()
                .map(v -> {
                    String path = v.getPropertyPath().toString();
                    return new ApiError.FieldError(path.substring(path.lastIndexOf('.') + 1), v.getMessage());
                })
                .toList();
        return respond(ApiError.of(HttpStatus.BAD_REQUEST, INVALID, fields));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> typeMismatch(MethodArgumentTypeMismatchException ex) {
        return respond(ApiError.of(HttpStatus.BAD_REQUEST, INVALID,
                List.of(new ApiError.FieldError(ex.getName(), "has an invalid value"))));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> missingParameter(MissingServletRequestParameterException ex) {
        return respond(ApiError.of(HttpStatus.BAD_REQUEST, INVALID,
                List.of(new ApiError.FieldError(ex.getParameterName(), "is required"))));
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ApiError> invalidRequest(InvalidRequestException ex) {
        return respond(ApiError.of(HttpStatus.BAD_REQUEST, INVALID,
                List.of(new ApiError.FieldError(ex.getField(), ex.getMessage()))));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> unreadable(HttpMessageNotReadableException ex) {
        return respond(ApiError.of(HttpStatus.BAD_REQUEST, "The request body could not be read"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> forbidden(AccessDeniedException ex) {
        return respond(ApiError.of(HttpStatus.FORBIDDEN, "You do not have permission to do this"));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> notFound(NotFoundException ex) {
        return respond(ApiError.of(HttpStatus.NOT_FOUND, ex.getMessage()));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> conflict(ConflictException ex) {
        return respond(ApiError.of(HttpStatus.CONFLICT, ex.getMessage()));
    }

    @ExceptionHandler(UpstreamException.class)
    public ResponseEntity<ApiError> upstream(UpstreamException ex) {
        log.warn("Upstream failure in an admin request: {}", ex.getMessage());
        return respond(ApiError.of(ex.isUnavailable() ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_GATEWAY,
                ex.getMessage()));
    }

    /** Safaricom could not be reached at all (connect or read timeout, DNS). */
    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<ApiError> unreachable(ResourceAccessException ex) {
        log.warn("Safaricom unreachable from an admin request: {}", ex.getMessage());
        return respond(ApiError.of(HttpStatus.SERVICE_UNAVAILABLE, "Safaricom could not be reached; try again shortly"));
    }

    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<ApiError> badGateway(RestClientException ex) {
        log.warn("Safaricom failed an admin request: {}", ex.getMessage());
        return respond(ApiError.of(HttpStatus.BAD_GATEWAY, "Safaricom returned an error"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception ex) {
        // Spring's own web errors (405, 406, 415...) keep their status, in the same body.
        if (ex instanceof ErrorResponse web) {
            HttpStatus status = HttpStatus.valueOf(web.getStatusCode().value());
            return respond(ApiError.of(status, status.getReasonPhrase()));
        }
        log.error("Unexpected failure in an admin request", ex);
        return respond(ApiError.of(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong"));
    }

    private static ResponseEntity<ApiError> respond(ApiError error) {
        return ResponseEntity.status(error.status()).body(error);
    }
}
