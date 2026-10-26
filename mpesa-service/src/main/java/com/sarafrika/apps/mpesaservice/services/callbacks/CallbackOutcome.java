package com.sarafrika.apps.mpesaservice.services.callbacks;

/** Lets a callback handler that still acks Safaricom tell the inbox why processing failed. Only records while
 * the inbox filter (or a replay) is capturing on this thread, so calling it anywhere else does nothing. */
public final class CallbackOutcome {

    static final int MAX_ERROR = 300;
    private static final ThreadLocal<String[]> CURRENT = new ThreadLocal<>();

    private CallbackOutcome() {
    }

    public static void failed(Throwable error) {
        failed(error == null ? "Unknown error" : describe(error));
    }

    public static void failed(String error) {
        String[] slot = CURRENT.get();
        if (slot != null && slot[0] == null) {
            slot[0] = shorten(error);
        }
    }

    static void begin() {
        CURRENT.set(new String[1]);
    }

    /** The failure a handler reported since begin(), or null; always stops capturing. */
    static String end() {
        String[] slot = CURRENT.get();
        CURRENT.remove();
        return slot == null ? null : slot[0];
    }

    // The class and message only: no stack trace ever reaches the inbox.
    static String describe(Throwable error) {
        String message = error.getMessage();
        String name = error.getClass().getSimpleName();
        return message == null || message.isBlank() ? name : name + ": " + message;
    }

    static String shorten(String text) {
        if (text == null) {
            return null;
        }
        String oneLine = text.replaceAll("\\s+", " ").trim();
        return oneLine.length() > MAX_ERROR ? oneLine.substring(0, MAX_ERROR - 1) + "…" : oneLine;
    }
}
