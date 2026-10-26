package com.sarafrika.apps.mpesaservice.exceptions;

/** The request clashes with the current state (a duplicate, a stale transition); admin endpoints answer 409. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
