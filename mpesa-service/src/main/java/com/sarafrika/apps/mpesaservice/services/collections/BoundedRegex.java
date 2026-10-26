package com.sarafrika.apps.mpesaservice.services.collections;

import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

// Operator-supplied account patterns, matched with a step budget so a pattern that backtracks
// catastrophically gives up quickly instead of stalling Safaricom's validation callback.
public final class BoundedRegex {

    public static final int MAX_PATTERN_LENGTH = 200;
    /** Account numbers (BillRefNumber) are short; anything longer cannot be a real one. */
    static final int MAX_INPUT_LENGTH = 100;
    static final long MAX_STEPS = 200_000;

    // Inputs that make nested or overlapping quantifiers explode; a pattern that trips on them is refused.
    private static final List<String> PROBES = List.of("a".repeat(40) + "!", "0".repeat(40) + "!",
            "A-".repeat(20) + "!", "ELM-" + "0".repeat(36) + "!", " ".repeat(40) + "!");

    private BoundedRegex() {
    }

    /** Thrown when a match uses up its step budget. */
    public static final class TooSlowException extends RuntimeException {
        TooSlowException() {
            super("The account pattern took too long to evaluate", null, false, false);
        }
    }

    /** Null when the pattern is acceptable, otherwise why not (too long, invalid, too slow). */
    public static String problem(String regex) {
        if (regex.length() > MAX_PATTERN_LENGTH) {
            return "must be at most " + MAX_PATTERN_LENGTH + " characters";
        }
        Pattern pattern;
        try {
            pattern = Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            return "is not a valid regular expression: " + e.getDescription();
        }
        try {
            PROBES.forEach(probe -> matches(pattern, probe));
        } catch (TooSlowException e) {
            return "is too slow to evaluate (nested or overlapping repetition); simplify it";
        }
        return null;
    }

    /** Whole-input match; throws {@link TooSlowException} once the budget is spent. */
    public static boolean matches(Pattern pattern, String input) {
        if (input.length() > MAX_INPUT_LENGTH) {
            return false;
        }
        return pattern.matcher(new Budgeted(input, MAX_STEPS)).matches();
    }

    /** Counts every character the matcher reads; backtracking reads the same characters again and again. */
    private static final class Budgeted implements CharSequence {
        private final String text;
        private final long[] left;

        Budgeted(String text, long budget) {
            this(text, new long[] {budget});
        }

        private Budgeted(String text, long[] left) {
            this.text = text;
            this.left = left;
        }

        @Override
        public char charAt(int index) {
            if (--left[0] < 0) {
                throw new TooSlowException();
            }
            return text.charAt(index);
        }

        @Override
        public int length() {
            return text.length();
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return new Budgeted(text.substring(start, end), left);
        }

        @Override
        public String toString() {
            return text;
        }
    }
}
