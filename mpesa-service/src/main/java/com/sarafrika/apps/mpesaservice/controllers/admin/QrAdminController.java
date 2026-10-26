package com.sarafrika.apps.mpesaservice.controllers.admin;

import com.sarafrika.apps.mpesaservice.dtos.admin.ApiError;
import com.sarafrika.apps.mpesaservice.dtos.admin.PageResponse;
import com.sarafrika.apps.mpesaservice.dtos.admin.qr.CreateQrCodeRequest;
import com.sarafrika.apps.mpesaservice.dtos.admin.qr.QrCodeDto;
import com.sarafrika.apps.mpesaservice.services.qr.QrCodeService;
import com.sarafrika.apps.mpesaservice.utils.PageRequests;
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
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/shortcodes/{uuid}/qr-codes")
@RequiredArgsConstructor
@Tag(name = "Admin: QR codes", description = "Daraja Dynamic QR codes customers scan to pay a paybill or till")
public class QrAdminController {

    static final Map<String, String> SORTABLE = Map.of("created_at", "createdAt");
    static final Sort DEFAULT_SORT = Sort.by(Sort.Order.desc("createdAt"));

    private final QrCodeService qrCodeService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('mpesa-operator')")
    @Operation(operationId = "createShortcodeQrCode", summary = "Generate a Dynamic QR code through Daraja (audited)")
    @ApiResponse(responseCode = "201", description = "The QR code, with the PNG as base64")
    @ApiResponse(responseCode = "400", description = "Invalid fields, or a TrxCode that does not fit the shortcode",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "Requires mpesa-operator",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "No such shortcode",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "A B2C or suspended shortcode",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "502", description = "Safaricom refused or answered without an image",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "503", description = "Safaricom could not be reached",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public QrCodeDto createShortcodeQrCode(@Parameter(description = "Shortcode uuid") @PathVariable("uuid") UUID uuid,
                                           @Valid @RequestBody CreateQrCodeRequest request) {
        return qrCodeService.create(uuid, request);
    }

    @GetMapping
    @PreAuthorize("hasRole('mpesa-viewer')")
    @Operation(operationId = "listShortcodeQrCodes", summary = "QR codes generated for a shortcode, newest first")
    @ApiResponse(responseCode = "200", description = "A page of QR codes (without images)")
    @ApiResponse(responseCode = "400", description = "Bad paging or sort value",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "Requires mpesa-viewer",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "No such shortcode",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public PageResponse<QrCodeDto> listShortcodeQrCodes(
            @Parameter(description = "Shortcode uuid") @PathVariable("uuid") UUID uuid,
            @Parameter(description = "Zero-based page number",
                    schema = @Schema(type = "integer", format = "int32", minimum = "0", defaultValue = "0"))
            @RequestParam(name = "page", required = false) Integer page,
            @Parameter(description = "Page size; values above 100 are clamped to 100",
                    schema = @Schema(type = "integer", format = "int32", minimum = "1", maximum = "100",
                            defaultValue = "25"))
            @RequestParam(name = "size", required = false) Integer size,
            @Parameter(description = "field,asc|desc. Fields: created_at. Default created_at,desc",
                    array = @ArraySchema(schema = @Schema(type = "string", example = "created_at,desc")))
            @RequestParam(name = "sort", required = false) List<String> sort) {
        return PageResponse.from(qrCodeService.list(uuid, PageRequests.of(page, size, sort, SORTABLE, DEFAULT_SORT)));
    }

    // No "produces": errors must still come back as ApiError JSON.
    @GetMapping("/{qr_uuid}/image")
    @PreAuthorize("hasRole('mpesa-viewer')")
    @Operation(operationId = "getShortcodeQrCodeImage", summary = "Download a QR code as PNG")
    @ApiResponse(responseCode = "200", description = "The PNG",
            content = @Content(mediaType = MediaType.IMAGE_PNG_VALUE,
                    schema = @Schema(type = "string", format = "binary")))
    @ApiResponse(responseCode = "403", description = "Requires mpesa-viewer",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "No such QR code for this shortcode",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiError.class)))
    public ResponseEntity<byte[]> getShortcodeQrCodeImage(
            @Parameter(description = "Shortcode uuid") @PathVariable("uuid") UUID uuid,
            @Parameter(description = "QR code uuid") @PathVariable("qr_uuid") UUID qrUuid) {
        QrCodeService.Image image = qrCodeService.image(uuid, qrUuid);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(image.fileName()).build().toString())
                .body(image.png());
    }
}
