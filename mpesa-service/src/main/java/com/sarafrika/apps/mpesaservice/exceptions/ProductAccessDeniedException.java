package com.sarafrika.apps.mpesaservice.exceptions;

/** A product called the machine API for a shortcode or operation it has not been granted; answered 403. */
public class ProductAccessDeniedException extends RuntimeException {

    public ProductAccessDeniedException(String message) {
        super(message);
    }
}
