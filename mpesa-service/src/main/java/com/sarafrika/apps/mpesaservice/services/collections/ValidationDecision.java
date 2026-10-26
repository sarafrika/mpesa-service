package com.sarafrika.apps.mpesaservice.services.collections;

/** What we tell Safaricom about one C2B payment, with the reason for people reading the logs or a dry run. */
public record ValidationDecision(boolean accepted, String resultCode, String reason) {

    public static ValidationDecision accept(String reason) {
        return new ValidationDecision(true, C2BResultCodes.ACCEPTED, reason);
    }

    public static ValidationDecision reject(String resultCode, String reason) {
        return new ValidationDecision(false, resultCode, reason);
    }
}
