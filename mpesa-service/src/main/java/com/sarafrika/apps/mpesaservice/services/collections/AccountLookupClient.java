package com.sarafrika.apps.mpesaservice.services.collections;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sarafrika.apps.mpesaservice.services.security.OutboundUrlGuard;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Asks a product whether a C2B account exists: POSTs {@link LookupRequest} as JSON and expects
 * 2xx with {@code {"valid": true|false, "reason"?: "...", "result_code"?: "C2B00011"}}. */
@Component
@Slf4j
public class AccountLookupClient {

    /** A lookup answer is a few fields; anything longer is not read. */
    static final int MAX_BODY_BYTES = 16 * 1024;
    private static final int MAX_REASON = 200;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final OutboundUrlGuard urlGuard;

    public AccountLookupClient(ObjectMapper objectMapper, OutboundUrlGuard urlGuard) {
        this.objectMapper = objectMapper;
        this.urlGuard = urlGuard;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public record LookupRequest(
            @JsonProperty("shortcode") String shortcode,
            @JsonProperty("account") String account,
            @JsonProperty("amount") BigDecimal amount,
            @JsonProperty("msisdn") String msisdn,
            @JsonProperty("trans_id") String transId) {
    }

    /** answered is false when the product could not be asked (timeout, network, non-2xx, unreadable body). */
    public record LookupResult(boolean answered, boolean valid, String reason, String resultCode) {

        static LookupResult failed(String reason) {
            return new LookupResult(false, false, reason, null);
        }
    }

    // Reasons stay generic: a dry run shows them to console users and must not describe this network.
    public LookupResult lookup(String url, int timeoutMs, LookupRequest body) {
        URI target;
        try {
            target = urlGuard.check(url);
        } catch (OutboundUrlGuard.BlockedUrlException e) {
            log.warn("Account lookup URL refused: {}", e.getMessage());
            return LookupResult.failed("The account lookup URL is not allowed");
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(target)
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            byte[] bytes;
            try (InputStream in = response.body()) {
                bytes = in.readNBytes(MAX_BODY_BYTES + 1);
            }
            if (response.statusCode() / 100 != 2) {
                return LookupResult.failed("Account lookup answered HTTP " + response.statusCode());
            }
            if (bytes.length > MAX_BODY_BYTES) {
                return LookupResult.failed("Account lookup answered with an oversized body");
            }
            JsonNode json = objectMapper.readTree(bytes);
            if (json == null || !json.path("valid").isBoolean()) {
                return LookupResult.failed("Account lookup answered without a boolean 'valid'");
            }
            String reason = text(json, "reason");
            return new LookupResult(true, json.get("valid").asBoolean(),
                    reason == null || reason.length() <= MAX_REASON ? reason : reason.substring(0, MAX_REASON),
                    text(json, "result_code"));
        } catch (HttpTimeoutException e) {
            return LookupResult.failed("Account lookup timed out after " + timeoutMs + " ms");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return LookupResult.failed("Account lookup was interrupted");
        } catch (Exception e) {
            log.warn("Account lookup at {} failed: {}", url, e.toString());
            return LookupResult.failed("Account lookup could not be reached");
        }
    }

    private static String text(JsonNode json, String field) {
        JsonNode node = json.get(field);
        return node == null || node.isNull() ? null : node.asText();
    }
}
