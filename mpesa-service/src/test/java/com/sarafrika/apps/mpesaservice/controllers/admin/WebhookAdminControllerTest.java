package com.sarafrika.apps.mpesaservice.controllers.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sarafrika.apps.mpesaservice.config.CallbackSecurityConfig;
import com.sarafrika.apps.mpesaservice.dtos.admin.products.WebhookDeliveryDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.products.WebhookHealthDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.products.WebhookSecretResponse;
import com.sarafrika.apps.mpesaservice.dtos.admin.products.WebhookSubscriptionDto;
import com.sarafrika.apps.mpesaservice.exceptions.InvalidRequestException;
import com.sarafrika.apps.mpesaservice.services.webhooks.WebhookAdminService;
import com.sarafrika.apps.mpesaservice.utils.enums.WebhookDeliveryStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(WebhookAdminController.class)
@Import(CallbackSecurityConfig.class)
class WebhookAdminControllerTest {

    private static final UUID SUBSCRIPTION = UUID.randomUUID();
    private static final UUID DELIVERY = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WebhookAdminService webhookService;

    @Test
    @WithMockUser(roles = "mpesa-viewer")
    void aViewerFiltersDeliveriesNewestFirst() throws Exception {
        when(webhookService.listDeliveries(any(), any()))
                .thenReturn(new PageImpl<>(List.of(delivery()), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/v1/admin/webhooks/deliveries").param("size", "10").param("status", "FAILED")
                        .param("client_id", "elimika-backend"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].event_type").value("c2b.confirmed"))
                .andExpect(jsonPath("$.items[0].status").value("FAILED"))
                .andExpect(jsonPath("$.items[0].latency_ms").value(5000));
        ArgumentCaptor<WebhookAdminService.DeliveryQuery> query =
                ArgumentCaptor.forClass(WebhookAdminService.DeliveryQuery.class);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(webhookService).listDeliveries(query.capture(), pageable.capture());
        assertThat(query.getValue()).isEqualTo(new WebhookAdminService.DeliveryQuery("elimika-backend", null,
                WebhookDeliveryStatus.FAILED, null));
        assertThat(pageable.getValue().getSort())
                .isEqualTo(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    }

    @Test
    @WithMockUser(roles = "mpesa-viewer")
    void anUnknownStatusOrSortIsRefused() throws Exception {
        mockMvc.perform(get("/api/v1/admin/webhooks/deliveries").param("status", "LOST"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field_errors[0].field").value("status"));
        mockMvc.perform(get("/api/v1/admin/webhooks/deliveries").param("sort", "payload,asc"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(webhookService);
    }

    @Test
    @WithMockUser(roles = "mpesa-admin")
    void anAdminRegistersAndRotatesWithTheSecretShownOnce() throws Exception {
        when(webhookService.create(any())).thenReturn(new WebhookSecretResponse(subscription(), "whsec_new"));
        when(webhookService.rotateSecret(SUBSCRIPTION)).thenReturn(new WebhookSecretResponse(subscription(),
                "whsec_rotated"));

        mockMvc.perform(post("/api/v1/admin/webhooks/subscriptions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"client_id\":\"elimika-backend\",\"url\":\"https://elimika.test/hooks\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.secret").value("whsec_new"))
                .andExpect(jsonPath("$.subscription.secret_hint").value("••••a91f"));
        mockMvc.perform(post("/api/v1/admin/webhooks/subscriptions/{uuid}/rotate-secret", SUBSCRIPTION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secret").value("whsec_rotated"));
        mockMvc.perform(get("/api/v1/admin/webhooks/subscriptions/{uuid}", SUBSCRIPTION))
                .andExpect(jsonPath("$.secret").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "mpesa-admin")
    void aPlainHttpUrlIsAFieldError() throws Exception {
        when(webhookService.update(eq(SUBSCRIPTION), any())).thenThrow(new InvalidRequestException("url",
                "must use https (http is allowed only for localhost)"));

        mockMvc.perform(patch("/api/v1/admin/webhooks/subscriptions/{uuid}", SUBSCRIPTION)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"url\":\"http://elimika.test/hooks\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field_errors[0].field").value("url"));
    }

    @Test
    @WithMockUser(roles = "mpesa-operator")
    void operatorsReplayAndTestButCannotConfigure() throws Exception {
        when(webhookService.replay(DELIVERY)).thenReturn(delivery());
        when(webhookService.sendTest(SUBSCRIPTION)).thenReturn(delivery());

        mockMvc.perform(post("/api/v1/admin/webhooks/deliveries/{uuid}/replay", DELIVERY))
                .andExpect(status().isAccepted());
        mockMvc.perform(post("/api/v1/admin/webhooks/subscriptions/{uuid}/test", SUBSCRIPTION))
                .andExpect(status().isAccepted());
        mockMvc.perform(post("/api/v1/admin/webhooks/subscriptions/{uuid}/rotate-secret", SUBSCRIPTION))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/webhooks/subscriptions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"client_id\":\"x\",\"url\":\"https://x.test\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "mpesa-viewer")
    void viewersCannotReplay() throws Exception {
        mockMvc.perform(post("/api/v1/admin/webhooks/deliveries/{uuid}/replay", DELIVERY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        verifyNoInteractions(webhookService);
    }

    @Test
    void anonymousCallersAreTurnedAway() throws Exception {
        mockMvc.perform(get("/api/v1/admin/webhooks/deliveries")).andExpect(status().is4xxClientError());
        verifyNoInteractions(webhookService);
    }

    private static WebhookSubscriptionDto subscription() {
        return new WebhookSubscriptionDto(SUBSCRIPTION, "elimika-backend", "https://elimika.test/hooks", List.of(),
                true, "••••a91f", null, WebhookHealthDto.of(true, 0, 0, 0, 0, null), null, null);
    }

    private static WebhookDeliveryDto delivery() {
        return new WebhookDeliveryDto(DELIVERY, SUBSCRIPTION, "elimika-backend", "Elimika", UUID.randomUUID(),
                "c2b.confirmed", "TJQ4K2L9XA", 5, WebhookDeliveryStatus.FAILED, null, 5000L, null,
                "Timed out waiting for the receiver", null, Instant.parse("2026-10-27T08:00:00Z"), null);
    }
}
