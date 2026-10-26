package com.sarafrika.apps.mpesaservice.controllers.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sarafrika.apps.mpesaservice.config.CallbackSecurityConfig;
import com.sarafrika.apps.mpesaservice.dtos.admin.products.ConnectProductResponse;
import com.sarafrika.apps.mpesaservice.dtos.admin.products.GrantableShortcodeDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.products.ProductDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.products.ProductGrantDto;
import com.sarafrika.apps.mpesaservice.exceptions.NotFoundException;
import com.sarafrika.apps.mpesaservice.services.products.ProductAdminService;
import com.sarafrika.apps.mpesaservice.utils.enums.GrantOperation;
import java.math.BigDecimal;
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

@WebMvcTest(ProductAdminController.class)
@Import(CallbackSecurityConfig.class)
class ProductAdminControllerTest {

    private static final UUID PAYBILL = UUID.randomUUID();
    private static final String CONNECT = """
            {"client_id":"elimika-backend","display_name":"Elimika",
             "grants":[{"shortcode_uuid":"%s","operations":["STK_PUSH","C2B_FEED"]}],
             "webhook_url":"https://api.elimika.test/hooks/mpesa"}""".formatted(PAYBILL);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductAdminService productService;

    @Test
    @WithMockUser(roles = "mpesa-viewer")
    void aViewerPagesThroughProductsByNameByDefault() throws Exception {
        when(productService.listProducts(isNull(), any()))
                .thenReturn(new PageImpl<>(List.of(product()), PageRequest.of(0, 25), 1));

        mockMvc.perform(get("/api/v1/admin/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].client_id").value("elimika-backend"))
                .andExpect(jsonPath("$.items[0].state").value("LIVE"))
                .andExpect(jsonPath("$.items[0].stats.api_calls_7d").value(41280))
                .andExpect(jsonPath("$.items[0].operations[0]").value("STK_PUSH"))
                .andExpect(jsonPath("$.total_items").value(1));
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(productService).listProducts(isNull(), pageable.capture());
        assertThat(pageable.getValue().getSort())
                .isEqualTo(Sort.by(Sort.Order.asc("displayName"), Sort.Order.desc("id")));
    }

    @Test
    @WithMockUser(roles = "mpesa-viewer")
    void unknownSortsAreRefused() throws Exception {
        mockMvc.perform(get("/api/v1/admin/products").param("sort", "secret,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field_errors[0].field").value("sort"));
        verifyNoInteractions(productService);
    }

    @Test
    @WithMockUser(roles = "mpesa-viewer")
    void anUnknownProductIs404() throws Exception {
        when(productService.getProduct("nobody")).thenThrow(new NotFoundException("Product not found: nobody"));

        mockMvc.perform(get("/api/v1/admin/products/{id}", "nobody"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Product not found: nobody"));
    }

    @Test
    @WithMockUser(roles = "mpesa-admin")
    void anAdminConnectsAProductAndSeesTheSecretOnce() throws Exception {
        when(productService.connect(any())).thenReturn(new ConnectProductResponse(product(), null, "whsec_abc"));

        mockMvc.perform(post("/api/v1/admin/products").contentType(MediaType.APPLICATION_JSON).content(CONNECT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.webhook_secret").value("whsec_abc"))
                .andExpect(jsonPath("$.product.display_name").value("Elimika"));
    }

    @Test
    @WithMockUser(roles = "mpesa-admin")
    void connectValidatesItsFields() throws Exception {
        mockMvc.perform(post("/api/v1/admin/products").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"client_id\":\"has spaces\",\"display_name\":\"\",\"grants\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field_errors[?(@.field=='client_id')]").exists())
                .andExpect(jsonPath("$.field_errors[?(@.field=='display_name')]").exists())
                .andExpect(jsonPath("$.field_errors[?(@.field=='grants')]").exists());
        verifyNoInteractions(productService);
    }

    @Test
    @WithMockUser(roles = {"mpesa-operator", "mpesa-finance"})
    void onlyAdminsChangeGrants() throws Exception {
        mockMvc.perform(post("/api/v1/admin/products").contentType(MediaType.APPLICATION_JSON).content(CONNECT))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        mockMvc.perform(put("/api/v1/admin/products/{id}/grants/{uuid}", "elimika-backend", PAYBILL)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"operations\":[\"STK_PUSH\"]}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/admin/products/{id}/grants/{uuid}", "elimika-backend", PAYBILL))
                .andExpect(status().isForbidden());
        verifyNoInteractions(productService);
    }

    @Test
    @WithMockUser(roles = "mpesa-admin")
    void anAdminUpsertsAndDeletesAGrant() throws Exception {
        when(productService.upsertGrant(eq("elimika-backend"), eq(PAYBILL), any())).thenReturn(grantDto());

        mockMvc.perform(put("/api/v1/admin/products/{id}/grants/{uuid}", "elimika-backend", PAYBILL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operations\":[\"STK_PUSH\",\"STATUS_LOOKUP\"],\"active\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortcode").value("600100"));
        mockMvc.perform(put("/api/v1/admin/products/{id}/grants/{uuid}", "elimika-backend", PAYBILL)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"operations\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field_errors[0].field").value("operations"));
        mockMvc.perform(put("/api/v1/admin/products/{id}/grants/{uuid}", "elimika-backend", PAYBILL)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"operations\":[\"FLY\"]}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(delete("/api/v1/admin/products/{id}/grants/{uuid}", "elimika-backend", PAYBILL))
                .andExpect(status().isNoContent());
        verify(productService).deleteGrant("elimika-backend", PAYBILL);
    }

    @Test
    @WithMockUser(roles = "mpesa-viewer")
    void grantsCanBeListedForOnePaybill() throws Exception {
        when(productService.listGrants(eq(PAYBILL), isNull(), any()))
                .thenReturn(new PageImpl<>(List.of(grantDto()), PageRequest.of(0, 25), 1));

        mockMvc.perform(get("/api/v1/admin/product-grants").param("shortcode_uuid", PAYBILL.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].product_name").value("Elimika"))
                .andExpect(jsonPath("$.items[0].operations.length()").value(2));
    }

    @Test
    @WithMockUser(roles = "mpesa-viewer")
    void grantableShortcodesAreListedWithoutCredentials() throws Exception {
        when(productService.listGrantableShortcodes(any())).thenReturn(new PageImpl<>(List.of(
                new GrantableShortcodeDto(PAYBILL, "600100", "Elimika Fees", "PAYBILL", "PRODUCTION", true)),
                PageRequest.of(0, 100), 1));

        mockMvc.perform(get("/api/v1/admin/product-grants/shortcodes").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].shortcode").value("600100"))
                .andExpect(jsonPath("$.items[0].consumer_secret").doesNotExist());
    }

    @Test
    void anonymousCallersAreTurnedAway() throws Exception {
        mockMvc.perform(get("/api/v1/admin/products")).andExpect(status().is4xxClientError());
        verifyNoInteractions(productService);
    }

    private static ProductDto product() {
        return new ProductDto("elimika-backend", "Elimika", ProductDto.State.LIVE,
                new ProductDto.Stats(new BigDecimal("8900000.00"), new BigDecimal("7200000.00"),
                        new BigDecimal("1700000.00"), 41280, 124, 0.003, null),
                List.of(grantDto()), List.of(GrantOperation.STK_PUSH, GrantOperation.STATUS_LOOKUP), List.of());
    }

    private static ProductGrantDto grantDto() {
        return new ProductGrantDto(UUID.randomUUID(), "elimika-backend", "Elimika", PAYBILL, "600100",
                "Elimika Fees", List.of(GrantOperation.STK_PUSH, GrantOperation.STATUS_LOOKUP), true, null,
                "jane.doe", null, null);
    }
}
