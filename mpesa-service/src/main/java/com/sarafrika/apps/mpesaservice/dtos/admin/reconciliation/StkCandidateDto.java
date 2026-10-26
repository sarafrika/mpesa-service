package com.sarafrika.apps.mpesaservice.dtos.admin.reconciliation;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sarafrika.apps.mpesaservice.services.reconciliation.ReconciliationLedger.StkCandidate;
import com.sarafrika.apps.mpesaservice.services.security.PersonalData;
import com.sarafrika.apps.mpesaservice.utils.Times;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** A settled STK payment without a receipt that a missing deposit may be; the phone is masked unless the caller
 * has mpesa-finance or mpesa-admin. */
@Schema(name = "StkCandidateDto")
public record StkCandidateDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @JsonProperty("payment_uuid") UUID paymentUuid,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "ws_CO_260520211133524545")
        @JsonProperty("checkout_request_id") String checkoutRequestId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @JsonProperty("amount") BigDecimal amount,
        @JsonProperty("phone_number") String phoneNumber,
        @JsonProperty("account_reference") String accountReference,
        @Schema(description = "The product that asked for the STK push") @JsonProperty("client_id") String clientId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "When the STK push was requested")
        @JsonProperty("requested_at") OffsetDateTime requestedAt,
        @Schema(description = "When it was settled (by its callback or a status query)")
        @JsonProperty("settled_at") OffsetDateTime settledAt) {

    public static StkCandidateDto from(StkCandidate c) {
        return new StkCandidateDto(c.uuid(), c.checkoutRequestId(), c.amount(),
                PersonalData.visible() ? c.phoneNumber() : PersonalData.maskPhone(c.phoneNumber()),
                c.accountReference(), c.clientId(), Times.offset(c.createdAt()), Times.offset(c.processedAt()));
    }
}
