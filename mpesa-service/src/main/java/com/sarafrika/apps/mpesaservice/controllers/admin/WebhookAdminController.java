package com.sarafrika.apps.mpesaservice.controllers.admin;

import com.sarafrika.apps.mpesaservice.dtos.admin.ApiError;
import com.sarafrika.apps.mpesaservice.dtos.admin.PageResponse;
import com.sarafrika.apps.mpesaservice.dtos.admin.products.CreateWebhookSubscriptionRequest;
import com.sarafrika.apps.mpesaservice.dtos.admin.products.UpdateWebhookSubscriptionRequest;
import com.sarafrika.apps.mpesaservice.dtos.admin.products.WebhookDeliveryDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.products.WebhookSecretResponse;
import com.sarafrika.apps.mpesaservice.dtos.admin.products.WebhookSubscriptionDto;
import com.sarafrika.apps.mpesaservice.services.webhooks.WebhookAdminService;
import com.sarafrika.apps.mpesaservice.services.webhooks.WebhookEvents;
import com.sarafrika.apps.mpesaservice.utils.PageRequests;
import com.sarafrika.apps.mpesaservice.utils.enums.WebhookDeliveryStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/webhooks")
@RequiredArgsConstructor
@Tag(name = "Admin: Webhooks", description = "Signed payment events sent to products, and every delivery attempt")
public class WebhookAdminController {

    static final Map<String, String> SUBSCRIPTION_SORTS = Map.of(
            "created_at", "createdAt", "client_id", "clientId", "url", "url");
    static final Sort SUBSCRIPTION_DEFAULT_SORT = Sort.by(Sort.Order.desc("createdAt"));
    static final Map<String, String> DELIVERY_SORTS = Map.of(
            "created_at", "createdAt", "status", "status", "latency_ms", "latencyMs", "event_type", "eventType");
    static final Sort DELIVERY_DEFAULT_SORT = Sort.by(Sort.Order.desc("createdAt"));

    private final WebhookAdminService webhookService;

    @GetMapping("/event-types")
    @PreAuthorize("hasRole('mpesa-viewer')")
    @Operation(operationId = "listWebhookEventTypes", summary = "Event types a webhook can subscribe to")
    public List<String> listWebhookEventTypes() {
        return WebhookEvents.SUBSCRIBABLE;
    }

    @GetMapping("/subscriptions")
    @PreAuthorize("hasRole('mpesa-viewer')")
    @Operation(operationId = "listWebhookSubscriptions", summary = "List webhook subscriptions with their health")
    @ApiResponse(responseCode = "200", description = "A page of subscriptions")
    @ApiResponse(responseCode = "400", description = "Negative page, size below 1 or an unknown sort field",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "Requires mpesa-viewer",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public PageResponse<WebhookSubscriptionDto> listWebhookSubscriptions(
            @Parameter(description = "Zero-based page number",
                    schema = @Schema(type = "integer", format = "int32", minimum = "0", defaultValue = "0"))
            @RequestParam(name = "page", required = false) Integer page,
            @Parameter(description = "Page size; values above 100 are clamped to 100",
                    schema = @Schema(type = "integer", format = "int32", minimum = "1", maximum = "100",
                            defaultValue = "25"))
            @RequestParam(name = "size", required = false) Integer size,
            @Parameter(description = "field,asc|desc; repeatable. Fields: created_at, client_id, url. "
                    + "Default created_at,desc",
                    array = @ArraySchema(schema = @Schema(type = "string", example = "created_at,desc")))
            @RequestParam(name = "sort", required = false) List<String> sort,
            @RequestParam(name = "client_id", required = false) String clientId) {
        return PageResponse.from(webhookService.listSubscriptions(clientId,
                PageRequests.of(page, size, sort, SUBSCRIPTION_SORTS, SUBSCRIPTION_DEFAULT_SORT)));
    }

    @PostMapping("/subscriptions")
    @PreAuthorize("hasRole('mpesa-admin')")
    @Operation(operationId = "createWebhookSubscription", summary = "Register a product's webhook",
            description = "The signing secret is in this response only.")
    @ApiResponse(responseCode = "201", description = "Registered; store the secret now")
    @ApiResponse(responseCode = "400", description = "Invalid URL or event type",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "Requires mpesa-admin",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ResponseEntity<WebhookSecretResponse> createWebhookSubscription(
            @Valid @RequestBody CreateWebhookSubscriptionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(webhookService.create(request));
    }

    @GetMapping("/subscriptions/{uuid}")
    @PreAuthorize("hasRole('mpesa-viewer')")
    @Operation(operationId = "getWebhookSubscription", summary = "One webhook subscription")
    @ApiResponse(responseCode = "404", description = "Not found",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public WebhookSubscriptionDto getWebhookSubscription(@PathVariable UUID uuid) {
        return webhookService.getSubscription(uuid);
    }

    @PatchMapping("/subscriptions/{uuid}")
    @PreAuthorize("hasRole('mpesa-admin')")
    @Operation(operationId = "updateWebhookSubscription", summary = "Change a webhook's URL, events or on/off state")
    @ApiResponse(responseCode = "400", description = "Invalid URL or event type",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "Requires mpesa-admin",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Not found",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public WebhookSubscriptionDto updateWebhookSubscription(@PathVariable UUID uuid,
                                                            @Valid @RequestBody UpdateWebhookSubscriptionRequest request) {
        return webhookService.update(uuid, request);
    }

    @DeleteMapping("/subscriptions/{uuid}")
    @PreAuthorize("hasRole('mpesa-admin')")
    @Operation(operationId = "deleteWebhookSubscription", summary = "Remove a webhook; pending deliveries then fail")
    @ApiResponse(responseCode = "204", description = "Removed")
    @ApiResponse(responseCode = "403", description = "Requires mpesa-admin",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Not found",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ResponseEntity<Void> deleteWebhookSubscription(@PathVariable UUID uuid) {
        webhookService.delete(uuid);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/subscriptions/{uuid}/rotate-secret")
    @PreAuthorize("hasRole('mpesa-admin')")
    @Operation(operationId = "rotateWebhookSecret", summary = "Replace a webhook's signing secret",
            description = "The new secret is in this response only; the old one stops signing straight away.")
    @ApiResponse(responseCode = "200", description = "Rotated; store the new secret now")
    @ApiResponse(responseCode = "403", description = "Requires mpesa-admin",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Not found",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public WebhookSecretResponse rotateWebhookSecret(@PathVariable UUID uuid) {
        return webhookService.rotateSecret(uuid);
    }

    @PostMapping("/subscriptions/{uuid}/test")
    @PreAuthorize("hasRole('mpesa-operator')")
    @Operation(operationId = "sendWebhookTestEvent", summary = "Send a signed test.ping event to a webhook")
    @ApiResponse(responseCode = "202", description = "Queued; follow it in the delivery list")
    @ApiResponse(responseCode = "403", description = "Requires mpesa-operator",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Not found",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "The webhook is disabled",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ResponseEntity<WebhookDeliveryDto> sendWebhookTestEvent(@PathVariable UUID uuid) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(webhookService.sendTest(uuid));
    }

    @GetMapping("/deliveries")
    @PreAuthorize("hasRole('mpesa-viewer')")
    @Operation(operationId = "listWebhookDeliveries", summary = "List webhook deliveries, newest first")
    @ApiResponse(responseCode = "200", description = "A page of deliveries, without payloads")
    @ApiResponse(responseCode = "400", description = "Negative page, size below 1 or an unknown sort field",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "Requires mpesa-viewer",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public PageResponse<WebhookDeliveryDto> listWebhookDeliveries(
            @Parameter(description = "Zero-based page number",
                    schema = @Schema(type = "integer", format = "int32", minimum = "0", defaultValue = "0"))
            @RequestParam(name = "page", required = false) Integer page,
            @Parameter(description = "Page size; values above 100 are clamped to 100",
                    schema = @Schema(type = "integer", format = "int32", minimum = "1", maximum = "100",
                            defaultValue = "25"))
            @RequestParam(name = "size", required = false) Integer size,
            @Parameter(description = "field,asc|desc; repeatable. Fields: created_at, status, latency_ms, "
                    + "event_type. Default created_at,desc",
                    array = @ArraySchema(schema = @Schema(type = "string", example = "created_at,desc")))
            @RequestParam(name = "sort", required = false) List<String> sort,
            @RequestParam(name = "client_id", required = false) String clientId,
            @RequestParam(name = "subscription_uuid", required = false) UUID subscriptionUuid,
            @RequestParam(name = "status", required = false) WebhookDeliveryStatus status,
            @RequestParam(name = "event_type", required = false) String eventType) {
        return PageResponse.from(webhookService.listDeliveries(
                new WebhookAdminService.DeliveryQuery(clientId, subscriptionUuid, status, eventType),
                PageRequests.of(page, size, sort, DELIVERY_SORTS, DELIVERY_DEFAULT_SORT)));
    }

    @GetMapping("/deliveries/{uuid}")
    @PreAuthorize("hasRole('mpesa-viewer')")
    @Operation(operationId = "getWebhookDelivery", summary = "One delivery with the exact payload sent")
    @ApiResponse(responseCode = "404", description = "Not found",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public WebhookDeliveryDto getWebhookDelivery(@PathVariable UUID uuid) {
        return webhookService.getDelivery(uuid);
    }

    @PostMapping("/deliveries/{uuid}/replay")
    @PreAuthorize("hasRole('mpesa-operator')")
    @Operation(operationId = "replayWebhookDelivery",
            summary = "Send a delivery again with the same delivery id and a fresh set of retries")
    @ApiResponse(responseCode = "202", description = "Queued")
    @ApiResponse(responseCode = "403", description = "Requires mpesa-operator",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Not found",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "Not attempted yet",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ResponseEntity<WebhookDeliveryDto> replayWebhookDelivery(@PathVariable UUID uuid) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(webhookService.replay(uuid));
    }
}
