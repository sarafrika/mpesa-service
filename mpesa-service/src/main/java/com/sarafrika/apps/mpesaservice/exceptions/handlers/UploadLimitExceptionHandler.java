package com.sarafrika.apps.mpesaservice.exceptions.handlers;

import com.sarafrika.apps.mpesaservice.dtos.admin.ApiError;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/** An upload over spring.servlet.multipart limits answers 413 in the admin ApiError body. It is not scoped to
 * the admin package: the multipart is parsed before a handler is chosen, so scoped advice never sees it. */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class UploadLimitExceptionHandler {

    private final DataSize maxFileSize;

    public UploadLimitExceptionHandler(
            @Value("${spring.servlet.multipart.max-file-size:1MB}") DataSize maxFileSize) {
        this.maxFileSize = maxFileSize;
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> tooLarge(MaxUploadSizeExceededException ex) {
        log.info("Upload refused: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(ApiError.of(HttpStatus.PAYLOAD_TOO_LARGE,
                "The file is larger than the " + label(maxFileSize) + " upload limit; upload a smaller file"));
    }

    static String label(DataSize size) {
        long bytes = size.toBytes();
        if (bytes >= DataSize.ofMegabytes(1).toBytes() && bytes % DataSize.ofMegabytes(1).toBytes() == 0) {
            return size.toMegabytes() + " MB";
        }
        return bytes >= 1024 && bytes % 1024 == 0 ? size.toKilobytes() + " KB" : bytes + " bytes";
    }
}
