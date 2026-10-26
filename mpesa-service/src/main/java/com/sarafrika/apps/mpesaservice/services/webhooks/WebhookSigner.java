package com.sarafrika.apps.mpesaservice.services.webhooks;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** X-Sarafrika-Signature: {@code t=<unix seconds>,v1=<hex HMAC-SHA256(secret, "<t>.<raw body>")>}.
 * Receivers recompute v1 over the raw body and reject stale t values to stop replays. */
public final class WebhookSigner {

    public static final String SIGNATURE_HEADER = "X-Sarafrika-Signature";
    public static final String EVENT_HEADER = "X-Sarafrika-Event";
    public static final String DELIVERY_HEADER = "X-Sarafrika-Delivery";
    private static final String SECRET_PREFIX = "whsec_";
    private static final SecureRandom RANDOM = new SecureRandom();

    private WebhookSigner() {
    }

    public static String signatureHeader(String secret, long unixSeconds, String body) {
        return "t=" + unixSeconds + ",v1=" + hmacHex(secret, unixSeconds + "." + body);
    }

    public static String hmacHex(String secret, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 is not available", e);
        }
    }

    /** True when the header carries a v1 signature of this body made with this secret. */
    public static boolean verify(String secret, String header, String body) {
        if (header == null) {
            return false;
        }
        String t = null;
        String v1 = null;
        for (String part : header.split(",")) {
            String[] pair = part.trim().split("=", 2);
            if (pair.length == 2 && pair[0].equals("t")) {
                t = pair[1];
            } else if (pair.length == 2 && pair[0].equals("v1")) {
                v1 = pair[1];
            }
        }
        if (t == null || v1 == null) {
            return false;
        }
        byte[] expected = hmacHex(secret, t + "." + body).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, v1.getBytes(StandardCharsets.UTF_8));
    }

    /** A new signing secret: "whsec_" and 32 random bytes, base64url. */
    public static String newSecret() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return SECRET_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** "••••" and the last four characters, the only form a secret is ever shown in after creation. */
    public static String hint(String secret) {
        return secret == null || secret.length() < 4 ? "••••" : "••••" + secret.substring(secret.length() - 4);
    }
}
