package com.sarafrika.apps.mpesaservice.services.collections;

import java.util.regex.Pattern;

/** Builds an account number that an account pattern accepts, for the dry run's pretend payment. Handles the
 * plain patterns operators write (literals, classes, \d \w, groups, |, ? * + {n,m}); anything else gives null. */
final class AccountSamples {

    private final String regex;
    private int pos;

    private AccountSamples(String regex) {
        this.regex = regex;
    }

    /** A matching account, checked against the pattern, or null when none could be built. */
    static String sample(String regex) {
        if (regex == null || regex.isBlank()) {
            return null;
        }
        try {
            AccountSamples parser = new AccountSamples(regex);
            String candidate = parser.alternatives();
            if (parser.pos != regex.length() || candidate.isEmpty()) {
                return null;
            }
            return BoundedRegex.matches(Pattern.compile(regex), candidate) ? candidate : null;
        } catch (RuntimeException unsupported) {
            return null;
        }
    }

    private String alternatives() {
        String first = sequence();
        while (pos < regex.length() && regex.charAt(pos) == '|') {
            pos++;
            sequence();
        }
        return first;
    }

    private String sequence() {
        StringBuilder out = new StringBuilder();
        while (pos < regex.length() && regex.charAt(pos) != '|' && regex.charAt(pos) != ')') {
            char c = regex.charAt(pos);
            if (c == '^' || c == '$') {
                pos++;
                continue;
            }
            String atom = atom();
            out.append(atom.repeat(repeats()));
        }
        return out.toString();
    }

    private String atom() {
        char c = regex.charAt(pos++);
        return switch (c) {
            case '(' -> {
                if (regex.startsWith("?:", pos)) {
                    pos += 2;
                }
                String inner = alternatives();
                expect(')');
                yield inner;
            }
            case '[' -> charClass();
            case '\\' -> escape(regex.charAt(pos++));
            case '.' -> "A";
            case '*', '+', '?', '{', ')', '|' -> throw new IllegalArgumentException("unexpected " + c);
            default -> String.valueOf(c);
        };
    }

    private String charClass() {
        if (regex.charAt(pos) == '^') {
            throw new IllegalArgumentException("negated class");
        }
        char first = regex.charAt(pos) == '\\' ? escape(regex.charAt(++pos)).charAt(0) : regex.charAt(pos);
        pos++;
        while (regex.charAt(pos) != ']') {
            pos += regex.charAt(pos) == '\\' ? 2 : 1;
        }
        pos++;
        return String.valueOf(first);
    }

    private static String escape(char c) {
        return switch (c) {
            case 'd' -> "0";
            case 'w' -> "A";
            case 'D', 'W', 's', 'S', 'b', 'B' -> throw new IllegalArgumentException("unsupported escape " + c);
            default -> String.valueOf(c);
        };
    }

    /** The smallest count the quantifier allows, at least 1 for * and ? so the sample stays readable. */
    private int repeats() {
        if (pos >= regex.length()) {
            return 1;
        }
        char c = regex.charAt(pos);
        if (c == '?' || c == '*' || c == '+') {
            pos++;
            skipLazy();
            return 1;
        }
        if (c == '{') {
            int close = regex.indexOf('}', pos);
            String[] bounds = regex.substring(pos + 1, close).split(",", -1);
            pos = close + 1;
            skipLazy();
            return Math.max(Integer.parseInt(bounds[0].trim()), 0);
        }
        return 1;
    }

    private void skipLazy() {
        if (pos < regex.length() && (regex.charAt(pos) == '?' || regex.charAt(pos) == '+')) {
            pos++;
        }
    }

    private void expect(char c) {
        if (pos >= regex.length() || regex.charAt(pos) != c) {
            throw new IllegalArgumentException("expected " + c);
        }
        pos++;
    }
}
