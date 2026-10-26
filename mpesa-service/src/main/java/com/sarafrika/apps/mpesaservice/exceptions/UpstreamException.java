package com.sarafrika.apps.mpesaservice.exceptions;

/** Daraja refused or failed a request (502), or could not be reached at all (503). */
public class UpstreamException extends RuntimeException {

    private final boolean unavailable;

    public UpstreamException(String message, boolean unavailable) {
        super(message);
        this.unavailable = unavailable;
    }

    public static UpstreamException badGateway(String message) {
        return new UpstreamException(message, false);
    }

    public static UpstreamException unavailable(String message) {
        return new UpstreamException(message, true);
    }

    public boolean isUnavailable() {
        return unavailable;
    }
}
