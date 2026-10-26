package com.sarafrika.apps.mpesaservice.dtos.admin.treasury;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sarafrika.apps.mpesaservice.models.B2bDestination;
import com.sarafrika.apps.mpesaservice.utils.Times;
import com.sarafrika.apps.mpesaservice.utils.enums.B2bReceiverType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;

/** A paybill or till this shortcode may sweep to. */
@Schema(name = "B2bDestinationDto")
public record B2bDestinationDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("uuid") UUID uuid,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("shortcode_uuid") UUID shortcodeUuid,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Sarafrika bank paybill")
        @JsonProperty("name") String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "522522")
        @JsonProperty("receiver_shortcode") String receiverShortcode,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("receiver_type") B2bReceiverType receiverType,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "BusinessPayBill",
                allowableValues = {"BusinessPayBill", "BusinessBuyGoods"})
        @JsonProperty("command_id") String commandId,
        @Schema(nullable = true, example = "1234567890", description = "Account at the receiving paybill")
        @JsonProperty("account_reference") String accountReference,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("active") boolean active,
        @JsonProperty("created_by") String createdBy,
        @JsonProperty("created_at") OffsetDateTime createdAt,
        @JsonProperty("updated_at") OffsetDateTime updatedAt) {

    public static B2bDestinationDto from(B2bDestination d) {
        return new B2bDestinationDto(d.getUuid(), d.getShortcodeUuid(), d.getName(), d.getReceiverShortcode(),
                d.getReceiverType(), d.getReceiverType().commandId(), d.getAccountReference(), d.isActive(),
                d.getCreatedBy(), Times.offset(d.getCreatedAt()), Times.offset(d.getUpdatedAt()));
    }
}
