package com.sarafrika.apps.mpesaservice.services.reconciliation;

import java.util.List;

/** The upload is not a usable M-Pesa organisation statement; rowErrors name the lines that failed (first 50). */
public class StatementParseException extends RuntimeException {

    public record RowError(int line, String message) {
    }

    private final List<RowError> rowErrors;

    public StatementParseException(String message) {
        this(message, List.of());
    }

    public StatementParseException(String message, List<RowError> rowErrors) {
        super(message);
        this.rowErrors = List.copyOf(rowErrors);
    }

    public List<RowError> rowErrors() {
        return rowErrors;
    }
}
