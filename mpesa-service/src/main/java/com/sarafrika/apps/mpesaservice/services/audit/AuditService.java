package com.sarafrika.apps.mpesaservice.services.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sarafrika.apps.mpesaservice.config.ClientIpResolver;
import com.sarafrika.apps.mpesaservice.dtos.admin.AuditEventDto;
import com.sarafrika.apps.mpesaservice.models.AuditEvent;
import com.sarafrika.apps.mpesaservice.repositories.AuditEventRepository;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Records who changed what, from where, with secrets redacted, and lists the trail newest first. */
@Service
@RequiredArgsConstructor
public class AuditService {

    static final String REDACTED = "[REDACTED]";
    static final String SYSTEM = "system";
    private static final Set<String> SECRET_KEYS = Set.of(
            "consumerkey", "consumersecret", "passkey", "initiatorpassword", "securitycredential");

    private final AuditEventRepository repository;
    private final ObjectMapper objectMapper;
    private final ClientIpResolver clientIpResolver;

    /** Before and after may be any JSON-serialisable value (a DTO, a map) or null. */
    @Transactional
    public AuditEvent record(String action, String entityType, String entityId, Object before, Object after) {
        AuditEvent event = new AuditEvent();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        event.setActor(actor(authentication));
        event.setActorName(actorName(authentication));
        event.setAction(action);
        event.setEntityType(entityType);
        event.setEntityId(entityId);
        event.setBefore(redactedJson(before));
        event.setAfter(redactedJson(after));
        event.setIp(currentIp());
        return repository.save(event);
    }

    @Transactional(readOnly = true)
    public Page<AuditEventDto> list(AuditQuery query, int page, int size) {
        PageRequest request = PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        return repository.findAll(matching(query), request).map(event -> AuditEventDto.from(event, objectMapper));
    }

    /** Optional exact-match filters; blank values are ignored. */
    public record AuditQuery(String actor, String action, String entityType, String entityId) {
    }

    private static Specification<AuditEvent> matching(AuditQuery query) {
        return (root, criteria, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            Map<String, String> filters = new LinkedHashMap<>();
            filters.put("actor", query.actor());
            filters.put("action", query.action());
            filters.put("entityType", query.entityType());
            filters.put("entityId", query.entityId());
            filters.forEach((field, value) -> {
                if (StringUtils.hasText(value)) {
                    predicates.add(cb.equal(root.get(field), value.trim()));
                }
            });
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    static String actor(Authentication authentication) {
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            return SYSTEM;
        }
        if (authentication instanceof JwtAuthenticationToken jwt) {
            String username = jwt.getToken().getClaimAsString("preferred_username");
            if (StringUtils.hasText(username)) {
                return username;
            }
            String subject = jwt.getToken().getSubject();
            if (StringUtils.hasText(subject)) {
                return subject;
            }
        }
        return StringUtils.hasText(authentication.getName()) ? authentication.getName() : SYSTEM;
    }

    private static String actorName(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwt) {
            String name = jwt.getToken().getClaimAsString("name");
            return StringUtils.hasText(name) ? name : null;
        }
        return null;
    }

    private String currentIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return clientIpResolver.resolve(attributes.getRequest());
        }
        return null;
    }

    private String redactedJson(Object value) {
        if (value == null) {
            return null;
        }
        JsonNode tree = value instanceof String text ? objectMapper.getNodeFactory().textNode(text)
                : objectMapper.valueToTree(value);
        return redact(tree).toString();
    }

    /** Replaces the value of every secret-looking key, at any depth. */
    static JsonNode redact(JsonNode node) {
        if (node instanceof ObjectNode object) {
            List<String> secrets = new ArrayList<>();
            for (Map.Entry<String, JsonNode> field : object.properties()) {
                if (isSecret(field.getKey())) {
                    secrets.add(field.getKey());
                } else {
                    redact(field.getValue());
                }
            }
            secrets.forEach(key -> {
                if (!object.get(key).isNull()) {
                    object.put(key, REDACTED);
                }
            });
        } else if (node instanceof ArrayNode array) {
            array.forEach(AuditService::redact);
        }
        return node;
    }

    static boolean isSecret(String key) {
        String normalized = key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return SECRET_KEYS.contains(normalized) || normalized.contains("secret")
                || normalized.contains("password") || normalized.contains("token");
    }
}
