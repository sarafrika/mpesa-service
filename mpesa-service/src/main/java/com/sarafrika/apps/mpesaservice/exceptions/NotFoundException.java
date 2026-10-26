package com.sarafrika.apps.mpesaservice.exceptions;

/** The requested record does not exist; admin endpoints answer 404. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
