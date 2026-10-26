package com.sarafrika.apps.mpesaservice.dtos.admin;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sarafrika.apps.mpesaservice.models.AuditEvent;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/** One audit trail entry; before and after are the redacted record as JSON, null when absent. */
@Schema(name = "AuditEventDto")
public record AuditEventDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("uuid") UUID uuid,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "jane.doe")
        @JsonProperty("actor") String actor,
        @Schema(example = "Jane Doe")
        @JsonProperty("actor_name") String actorName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "SHORTCODE_UPDATED")
        @JsonProperty("action") String action,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "SHORTCODE")
        @JsonProperty("entity_type") String entityType,
        @Schema(example = "550e8400-e29b-41d4-a716-446655440000")
        @JsonProperty("entity_id") String entityId,
        @Schema(type = "object", description = "The record before the change, secrets redacted")
        @JsonProperty("before") JsonNode before,
        @Schema(type = "object", description = "The record after the change, secrets redacted")
        @JsonProperty("after") JsonNode after,
        @Schema(example = "196.201.214.200")
        @JsonProperty("ip") String ip,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("created_at") Instant createdAt) {

    public static AuditEventDto from(AuditEvent event, ObjectMapper mapper) {
        return new AuditEventDto(event.getUuid(), event.getActor(), event.getActorName(), event.getAction(),
                event.getEntityType(), event.getEntityId(), parse(event.getBefore(), mapper),
                parse(event.getAfter(), mapper), event.getIp(), event.getCreatedAt());
    }

    private static JsonNode parse(String json, ObjectMapper mapper) {
        if (json == null) {
            return null;
        }
        try {
            return mapper.readTree(json);
        } catch (JsonProcessingException e) {
            return mapper.getNodeFactory().textNode(json);
        }
    }
}
