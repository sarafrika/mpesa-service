package com.sarafrika.apps.mpesaservice.exceptions;

/** The maker of an approval request tried to decide it; answered 403 with the message. */
public class MakerCheckerException extends RuntimeException {

    public MakerCheckerException(String message) {
        super(message);
    }
}
