package com.sarafrika.apps.mpesaservice.exceptions.handlers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/** Uploads over the servlet multipart limit answer 413 with the ApiError body and the limit spelled out. */
class UploadLimitExceptionHandlerTest {

    @RestController
    static class Uploads {
        @PostMapping("/upload")
        void upload() {
            throw new MaxUploadSizeExceededException(DataSize.ofMegabytes(6).toBytes());
        }
    }

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new Uploads())
            .setControllerAdvice(new UploadLimitExceptionHandler(DataSize.ofMegabytes(6))).build();

    @Test
    void anUploadOverTheLimitIs413WithAClearMessage() throws Exception {
        mockMvc.perform(multipart("/upload").file(new MockMultipartFile("file", "big.csv", "text/csv", new byte[8])))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.status").value(413))
                .andExpect(jsonPath("$.message").value(
                        "The file is larger than the 6 MB upload limit; upload a smaller file"))
                .andExpect(jsonPath("$.field_errors").isEmpty());
    }

    @Test
    void limitsReadAsPeopleWriteThem() {
        org.assertj.core.api.Assertions.assertThat(UploadLimitExceptionHandler.label(DataSize.ofKilobytes(512)))
                .isEqualTo("512 KB");
        org.assertj.core.api.Assertions.assertThat(UploadLimitExceptionHandler.label(DataSize.ofBytes(1500)))
                .isEqualTo("1500 bytes");
    }
}
