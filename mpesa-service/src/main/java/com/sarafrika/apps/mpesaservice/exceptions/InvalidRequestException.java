package com.sarafrika.apps.mpesaservice.exceptions;

/** A request parameter is unacceptable; admin endpoints answer 400 with it in field_errors. */
public class InvalidRequestException extends RuntimeException {

    private final String field;

    public InvalidRequestException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
