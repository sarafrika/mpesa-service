package com.sarafrika.apps.mpesaservice.exceptions.handlers;

import com.sarafrika.apps.mpesaservice.dtos.admin.ApiError;
import com.sarafrika.apps.mpesaservice.exceptions.ConflictException;
import com.sarafrika.apps.mpesaservice.exceptions.NotFoundException;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Every admin endpoint fails with the same {@link ApiError} body. */
@RestControllerAdvice(basePackages = "com.sarafrika.apps.mpesaservice.controllers.admin")
@Slf4j
public class AdminExceptionHandler {

    private static final String INVALID = "The request is invalid";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> invalidBody(MethodArgumentNotValidException ex) {
        List<ApiError.FieldError> fields = ex.getBindingResult().getAllErrors().stream()
                .map(error -> new ApiError.FieldError(
                        error instanceof FieldError field ? field.getField() : error.getObjectName(),
                        error.getDefaultMessage()))
                .toList();
        return respond(ApiError.of(HttpStatus.BAD_REQUEST, INVALID, fields));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiError> invalidParameters(HandlerMethodValidationException ex) {
        List<ApiError.FieldError> fields = ex.getParameterValidationResults().stream()
                .flatMap(result -> {
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

    private static ResponseEntity<ApiError> respond(ApiError error) {
        return ResponseEntity.status(error.status()).body(error);
    }
}
