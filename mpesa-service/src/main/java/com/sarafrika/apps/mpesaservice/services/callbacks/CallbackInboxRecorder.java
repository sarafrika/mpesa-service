package com.sarafrika.apps.mpesaservice.services.callbacks;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sarafrika.apps.mpesaservice.models.CallbackInboxEntry;
import com.sarafrika.apps.mpesaservice.repositories.CallbackInboxRepository;
import com.sarafrika.apps.mpesaservice.services.callbacks.CallbackAcks.Processing;
import com.sarafrika.apps.mpesaservice.utils.enums.CallbackKind;
import com.sarafrika.apps.mpesaservice.utils.enums.CallbackStatus;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** Writes inbox rows for the filter. Every method swallows its own failures: the inbox must never stop a
 * callback from being handled or acknowledged. */
@Slf4j
@Component
public class CallbackInboxRecorder {

    /** Key holding a body that was not JSON; such an entry cannot be replayed. */
    public static final String UNPARSED = "_unparsed";

    private final CallbackInboxRepository repository;
    private final CallbackReferences references;
    private final CallbackInboxProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public CallbackInboxRecorder(CallbackInboxRepository repository, CallbackReferences references,
                                 CallbackInboxProperties properties, ObjectMapper objectMapper,
                                 ObjectProvider<Clock> clock) {
        this.repository = repository;
        this.references = references;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock.getIfUnique(Clock::systemUTC);
    }

    /** Stores the request as RECEIVED and returns its uuid, or null when it could not be stored. */
    public UUID received(String endpoint, String sourceIp, Map<String, String> headers, byte[] body) {
        try {
            CallbackInboxEntry entry = new CallbackInboxEntry();
            entry.setEndpoint(truncate(endpoint, 200));
            entry.setKind(CallbackKind.fromPath(endpoint));
            entry.setSourceIp(truncate(sourceIp, 64));
            entry.setHeaders(headers == null || headers.isEmpty() ? null : objectMapper.writeValueAsString(headers));
            entry.setBodySize(body == null ? 0 : body.length);
            boolean tooLarge = body != null && body.length > properties.maxBodyBytes();
            JsonNode tree = tooLarge ? null : parse(body);
            if (tooLarge) {
                entry.setBodyTruncated(true);
            } else if (tree != null) {
                entry.setBody(tree.toString());
            } else if (body != null && body.length > 0) {
                // Not JSON: kept as a string so the inbox still shows what arrived.
                entry.setBody(objectMapper.writeValueAsString(Map.of(UNPARSED,
                        new String(body, StandardCharsets.UTF_8).replace("\u0000", ""))));
            }
            entry.setReference(CallbackReferences.searchText(tree));
            entry.setStatus(CallbackStatus.RECEIVED);
            entry.setAttempts(1);
            Instant now = clock.instant();
            entry.setReceivedAt(now);
            entry.setLastAttemptAt(now);
            return repository.saveAndFlush(entry).getUuid();
        } catch (Exception e) {
            log.error("Could not record callback to {} in the inbox: {}", endpoint, e.toString());
            return null;
        }
    }

    /** Marks how the handler did and links the payment the callback is about. */
    public void finished(UUID uuid, Processing processing) {
        if (uuid == null) {
            return;
        }
        try {
            repository.findByUuid(uuid).ifPresent(entry -> {
                entry.setStatus(processing.ok() ? CallbackStatus.PROCESSED : CallbackStatus.FAILED);
                entry.setError(processing.ok() ? null : processing.error());
                link(entry);
                repository.save(entry);
            });
        } catch (Exception e) {
            log.error("Could not record the outcome of callback {} in the inbox: {}", uuid, e.toString());
        }
    }

    void link(CallbackInboxEntry entry) {
        if (entry.getPaymentUuid() != null || entry.getBody() == null) {
            return;
        }
        try {
            references.paymentFor(entry.getKind(), objectMapper.readTree(entry.getBody()))
                    .ifPresent(entry::setPaymentUuid);
        } catch (Exception e) {
            log.warn("Could not link callback {} to a payment: {}", entry.getUuid(), e.toString());
        }
    }

    private JsonNode parse(byte[] body) {
        if (body == null || body.length == 0) {
            return null;
        }
        try {
            JsonNode tree = objectMapper.readTree(body);
            // Postgres jsonb cannot hold \u0000, so such a body is kept as text instead.
            return tree == null || tree.isMissingNode() || tree.toString().contains("\\u0000") ? null : tree;
        } catch (Exception notJson) {
            return null;
        }
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
