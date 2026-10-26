package com.sarafrika.apps.mpesaservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/** Writes the OpenAPI spec to build/openapi/openapi.json; mpesa-ui generates its Orval client from it. */
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiSpecExportTest {

    static final Path SPEC = Path.of("build", "openapi", "openapi.json");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @WithMockUser
    void exportsTheOpenApiSpec() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode spec = objectMapper.readTree(body);
        // A fixed server list keeps the file identical between runs; the UI proxy supplies the host.
        ((com.fasterxml.jackson.databind.node.ObjectNode) spec).remove("servers");
        assertThat(spec.path("paths").has("/api/v1/mpesa/stk-push")).isTrue();

        // Springdoc's property order varies between runs; sorted keys keep the committed spec stable for CI.
        Object sorted = objectMapper.convertValue(spec, Object.class);
        String json = objectMapper.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
                .writerWithDefaultPrettyPrinter().writeValueAsString(sorted);
        Files.createDirectories(SPEC.getParent());
        Files.writeString(SPEC, json + "\n");
    }
}
