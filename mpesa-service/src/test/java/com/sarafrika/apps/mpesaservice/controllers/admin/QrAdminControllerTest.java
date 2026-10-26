package com.sarafrika.apps.mpesaservice.controllers.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sarafrika.apps.mpesaservice.config.CallbackSecurityConfig;
import com.sarafrika.apps.mpesaservice.dtos.admin.qr.QrCodeDto;
import com.sarafrika.apps.mpesaservice.exceptions.NotFoundException;
import com.sarafrika.apps.mpesaservice.exceptions.UpstreamException;
import com.sarafrika.apps.mpesaservice.services.qr.QrCodeService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(QrAdminController.class)
@Import(CallbackSecurityConfig.class)
class QrAdminControllerTest {

    private static final UUID CODE = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID QR = UUID.fromString("66666666-7777-8888-9999-000000000000");
    private static final String BASE = "/api/v1/admin/shortcodes/" + CODE + "/qr-codes";
    private static final QrCodeDto DTO = new QrCodeDto(QR, CODE, "Elimika Fees", "ELM-1", new BigDecimal("1500"),
            "PB", "600100", 300, "16738-1", "jane", OffsetDateTime.parse("2026-10-26T14:32:08+03:00"),
            BASE + "/" + QR + "/image", "iVBORw0KGgo=");
    private static final String BODY = """
            {"merchant_name":"Elimika Fees","ref_no":"ELM-1","amount":1500,"trx_code":"PB","size":300}""";

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private QrCodeService qrCodeService;

    @Test
    @WithMockUser(roles = "mpesa-operator")
    void anOperatorGeneratesACode() throws Exception {
        when(qrCodeService.create(eq(CODE), any())).thenReturn(DTO);
        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.trx_code").value("PB"))
                .andExpect(jsonPath("$.image_base64").value("iVBORw0KGgo="));
    }

    @Test
    @WithMockUser(roles = "mpesa-operator")
    void invalidFieldsReachTheForm() throws Exception {
        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"merchant_name\":\"\",\"ref_no\":\"R\",\"trx_code\":\"SM\",\"size\":50}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field_errors.length()").value(3));
        verifyNoInteractions(qrCodeService);
    }

    @Test
    @WithMockUser(roles = "mpesa-operator")
    void darajaFailuresAre502And503() throws Exception {
        when(qrCodeService.create(eq(CODE), any())).thenThrow(UpstreamException.unavailable("Safaricom down"));
        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isServiceUnavailable());
        when(qrCodeService.create(eq(CODE), any())).thenThrow(UpstreamException.badGateway("refused"));
        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadGateway());
    }

    @Test
    @WithMockUser(roles = "mpesa-viewer")
    void aViewerListsAndDownloadsButCannotGenerate() throws Exception {
        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());

        when(qrCodeService.list(eq(CODE), any())).thenReturn(new PageImpl<>(List.of(DTO), PageRequest.of(0, 25), 1));
        mockMvc.perform(get(BASE)).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].cpi").value("600100"));

        when(qrCodeService.image(CODE, QR)).thenReturn(new QrCodeService.Image(new byte[]{1, 2}, "qr-600100-ELM-1.png"));
        mockMvc.perform(get(BASE + "/" + QR + "/image"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"qr-600100-ELM-1.png\""))
                .andExpect(content().bytes(new byte[]{1, 2}));
    }

    @Test
    @WithMockUser(roles = "mpesa-viewer")
    void aMissingImageIsApiErrorJson() throws Exception {
        when(qrCodeService.image(CODE, QR)).thenThrow(new NotFoundException("No QR code"));
        mockMvc.perform(get(BASE + "/" + QR + "/image"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No QR code"));
    }
}
