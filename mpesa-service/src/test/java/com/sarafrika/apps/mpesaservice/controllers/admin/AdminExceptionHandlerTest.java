package com.sarafrika.apps.mpesaservice.controllers.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sarafrika.apps.mpesaservice.config.CallbackSecurityConfig;
import com.sarafrika.apps.mpesaservice.exceptions.ConflictException;
import com.sarafrika.apps.mpesaservice.exceptions.NotFoundException;
import com.sarafrika.apps.mpesaservice.exceptions.UpstreamException;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

/** Admin endpoints fail with one ApiError shape: status, error, message and field_errors. */
@WebMvcTest(AdminExceptionHandlerTest.ProbeController.class)
@Import({CallbackSecurityConfig.class, AdminExceptionHandlerTest.ProbeController.class})
@WithMockUser(roles = "mpesa-viewer")
class AdminExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void anInvalidBodyListsItsFieldErrors() throws Exception {
        mockMvc.perform(post("/api/v1/admin/probe").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.field_errors[0].field").value("name"));
    }

    @Test
    void bodyFieldErrorsUseTheJsonNamesEvenWhenNested() throws Exception {
        mockMvc.perform(post("/api/v1/admin/probe/snake").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"business_name\":\"\",\"grants\":[{\"shortcode_uuid\":null}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field_errors[?(@.field == 'business_name')]").exists())
                .andExpect(jsonPath("$.field_errors[?(@.field == 'grants[0].shortcode_uuid')]").exists())
                .andExpect(jsonPath("$.field_errors[?(@.field == 'businessName')]").doesNotExist());
    }

    @Test
    void anInvalidParameterIsReportedByItsRequestName() throws Exception {
        mockMvc.perform(get("/api/v1/admin/probe").param("page_size", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field_errors[0].field").value("page_size"));
    }

    @Test
    void missingRecordsAnswer404AndClashesAnswer409() throws Exception {
        mockMvc.perform(get("/api/v1/admin/probe/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Probe missing not found"))
                .andExpect(jsonPath("$.field_errors").isEmpty());
        mockMvc.perform(get("/api/v1/admin/probe/taken"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void aMissingRoleAnswers403() throws Exception {
        mockMvc.perform(post("/api/v1/admin/probe/admin-only"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    void safaricomFailuresAnswer502And503WithoutInternals() throws Exception {
        mockMvc.perform(get("/api/v1/admin/probe/upstream"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.message").value("Daraja rejected the request"))
                .andExpect(jsonPath("$.field_errors").isArray());
        mockMvc.perform(get("/api/v1/admin/probe/unreachable"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("Service Unavailable"))
                .andExpect(jsonPath("$.message").value("Safaricom could not be reached; try again shortly"));
        mockMvc.perform(get("/api/v1/admin/probe/http-502"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("Safaricom returned an error"));
    }

    @Test
    void anythingElseIsA500WithNoStackTrace() throws Exception {
        mockMvc.perform(get("/api/v1/admin/probe/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Something went wrong"))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.field_errors").isEmpty());
    }

    @RestController
    @RequestMapping("/api/v1/admin/probe")
    static class ProbeController {

        record Body(@NotBlank String name) {
        }

        record Grant(@NotNull @JsonProperty("shortcode_uuid") String shortcodeUuid) {
        }

        record SnakeBody(@NotBlank @JsonProperty("business_name") String businessName,
                         @JsonProperty("grants") List<@Valid Grant> grants) {
        }

        @PostMapping("/snake")
        String snake(@Valid @RequestBody SnakeBody body) {
            return body.businessName();
        }

        @GetMapping
        String list(@RequestParam(name = "page_size", defaultValue = "25") @Max(100) int pageSize) {
            return "ok";
        }

        @PostMapping
        String create(@Valid @RequestBody Body body) {
            return body.name();
        }

        @GetMapping("/{name}")
        String get(@PathVariable String name) {
            switch (name) {
                case "upstream" -> throw UpstreamException.badGateway("Daraja rejected the request");
                case "unreachable" -> throw new ResourceAccessException("Connect timed out");
                case "http-502" -> throw HttpServerErrorException.create(HttpStatus.BAD_GATEWAY, "Bad Gateway",
                        HttpHeaders.EMPTY, null, null);
                case "boom" -> throw new IllegalStateException("internal detail");
                default -> { }
            }
            if ("taken".equals(name)) {
                throw new ConflictException("Probe taken already exists");
            }
            throw new NotFoundException("Probe " + name + " not found");
        }

        @PostMapping("/admin-only")
        @PreAuthorize("hasRole('mpesa-admin')")
        public String adminOnly() {
            return "ok";
        }
    }
}
