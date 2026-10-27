package com.sarafrika.apps.mpesaservice.services.qr;

import com.sarafrika.apps.mpesaservice.dtos.MpesaApiResponse;
import com.sarafrika.apps.mpesaservice.dtos.MpesaErrorResponse;
import com.sarafrika.apps.mpesaservice.dtos.QRCodeResponse;
import com.sarafrika.apps.mpesaservice.dtos.admin.qr.CreateQrCodeRequest;
import com.sarafrika.apps.mpesaservice.dtos.admin.qr.QrCodeDto;
import com.sarafrika.apps.mpesaservice.exceptions.ConflictException;
import com.sarafrika.apps.mpesaservice.exceptions.InvalidRequestException;
import com.sarafrika.apps.mpesaservice.exceptions.NotFoundException;
import com.sarafrika.apps.mpesaservice.exceptions.UpstreamException;
import com.sarafrika.apps.mpesaservice.models.MpesaShortCode;
import com.sarafrika.apps.mpesaservice.models.QrCode;
import com.sarafrika.apps.mpesaservice.repositories.MpesaShortCodeRepository;
import com.sarafrika.apps.mpesaservice.repositories.QrCodeRepository;
import com.sarafrika.apps.mpesaservice.services.MpesaDarajaService;
import com.sarafrika.apps.mpesaservice.services.audit.AuditService;
import com.sarafrika.apps.mpesaservice.services.audit.CurrentActor;
import com.sarafrika.apps.mpesaservice.services.shortcode.ShortcodeCapabilities;
import com.sarafrika.apps.mpesaservice.utils.enums.ShortcodeCapability;
import com.sarafrika.apps.mpesaservice.utils.enums.QRTransactionType;
import com.sarafrika.apps.mpesaservice.utils.enums.ShortcodeType;
import java.time.ZoneId;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Dynamic QR codes through Daraja for a paybill (TrxCode PB) or till (BG); the shortcode is the CPI. */
@Service
@RequiredArgsConstructor
public class QrCodeService {

    static final int DEFAULT_SIZE = 300;
    static final String AUDIT_ACTION = "QR_CODE_CREATED";
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G'};

    private final MpesaShortCodeRepository shortCodeRepository;
    private final QrCodeRepository repository;
    private final MpesaDarajaService darajaService;
    private final AuditService auditService;

    /** A PNG and the file name to download it under. */
    public record Image(byte[] png, String fileName) {
    }

    public QrCodeDto create(UUID shortcodeUuid, CreateQrCodeRequest request) {
        MpesaShortCode shortcode = shortcode(shortcodeUuid);
        ShortcodeCapabilities.require(shortcode, ShortcodeCapability.QR);
        if (!Boolean.TRUE.equals(shortcode.getIsActive())) {
            throw new ConflictException("Shortcode " + shortcode.getShortcode() + " is suspended; it takes no payments");
        }
        if (request.amount() == null || request.amount().stripTrailingZeros().scale() > 0) {
            throw new InvalidRequestException("amount", "must be whole shillings");
        }
        QRTransactionType type = type(shortcode, request.trxCode());
        int size = request.size() == null ? DEFAULT_SIZE : request.size();
        String merchantName = request.merchantName().trim();
        String refNo = request.refNo().trim();

        MpesaApiResponse<QRCodeResponse> response = darajaService.generateDynamicQRCode(shortcodeUuid, merchantName,
                refNo, request.amount(), type, size);
        QRCodeResponse answer = response.success() ? response.data() : null;
        if (answer == null) {
            throw upstream(response);
        }
        if (!answer.isSuccessful()) {
            throw UpstreamException.badGateway("Safaricom answered without a QR image"
                    + (answer.responseDescription() == null ? "" : " (" + answer.responseDescription() + ")"));
        }
        String image = answer.qrCode().replaceAll("\\s", "");
        if (!isPng(image)) {
            throw UpstreamException.badGateway("Safaricom's QR image is not a PNG");
        }

        QrCode code = new QrCode();
        code.setShortcodeUuid(shortcodeUuid);
        code.setMerchantName(merchantName);
        code.setRefNo(refNo);
        code.setAmount(request.amount());
        code.setTrxCode(type.getCode());
        code.setCpi(shortcode.getShortcode());
        code.setSize(size);
        code.setRequestId(answer.requestId());
        code.setImageBase64(image);
        code.setCreatedBy(CurrentActor.name());
        QrCode saved = repository.save(code);

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("shortcode_uuid", shortcodeUuid);
        after.put("merchant_name", merchantName);
        after.put("ref_no", refNo);
        after.put("amount", request.amount());
        after.put("trx_code", type.getCode());
        after.put("size", size);
        auditService.record(AUDIT_ACTION, "QR_CODE", saved.getUuid().toString(), null, after);
        return toDto(saved, true);
    }

    @Transactional(readOnly = true)
    public Page<QrCodeDto> list(UUID shortcodeUuid, Pageable pageable) {
        shortcode(shortcodeUuid);
        return repository.findByShortcodeUuid(shortcodeUuid, pageable).map(code -> toDto(code, false));
    }

    @Transactional(readOnly = true)
    public Image image(UUID shortcodeUuid, UUID uuid) {
        QrCode code = repository.findByUuidAndShortcodeUuid(uuid, shortcodeUuid)
                .orElseThrow(() -> new NotFoundException("No QR code " + uuid + " for this shortcode"));
        String ref = code.getRefNo().replaceAll("[^A-Za-z0-9_-]", "_");
        return new Image(Base64.getDecoder().decode(code.getImageBase64()),
                "qr-" + code.getCpi() + "-" + ref + ".png");
    }

    private MpesaShortCode shortcode(UUID uuid) {
        return shortCodeRepository.findByUuid(uuid).filter(code -> !code.isDeleted())
                .orElseThrow(() -> new NotFoundException("No shortcode " + uuid));
    }

    /** PB pays a paybill and BG a till (Daraja TrxCode); the other codes never take our shortcode as CPI. */
    static QRTransactionType type(MpesaShortCode shortcode, String trxCode) {
        QRTransactionType expected = shortcode.getShortcodeType() == ShortcodeType.TILL
                ? QRTransactionType.BUY_GOODS : QRTransactionType.PAY_BILL;
        if (trxCode == null || trxCode.isBlank()) {
            return expected;
        }
        QRTransactionType requested = QRTransactionType.fromCode(trxCode.trim());
        if (requested != expected) {
            throw new InvalidRequestException("trx_code", "a " + ShortcodeCapabilities.label(shortcode.getShortcodeType())
                    + " takes " + expected.getCode());
        }
        return requested;
    }

    static UpstreamException upstream(MpesaApiResponse<?> response) {
        MpesaErrorResponse error = response.error();
        String code = error == null ? null : error.errorCode();
        if ("AUTH_FAILED".equals(code)) {
            return UpstreamException.badGateway("Daraja did not issue a token for this shortcode's credentials");
        }
        if (code == null || MpesaErrorResponse.NETWORK_ERROR.equals(code) || "INTERNAL_ERROR".equals(code)) {
            return UpstreamException.unavailable("Safaricom could not be reached to generate the QR code; try again shortly");
        }
        String message = error.errorMessage();
        return UpstreamException.badGateway("Safaricom refused to generate the QR code"
                + (message == null || message.isBlank() ? "" : " (" + message + ")"));
    }

    static boolean isPng(String base64) {
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            if (bytes.length < PNG_SIGNATURE.length) {
                return false;
            }
            for (int i = 0; i < PNG_SIGNATURE.length; i++) {
                if (bytes[i] != PNG_SIGNATURE[i]) {
                    return false;
                }
            }
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    static QrCodeDto toDto(QrCode code, boolean withImage) {
        return new QrCodeDto(code.getUuid(), code.getShortcodeUuid(), code.getMerchantName(), code.getRefNo(),
                code.getAmount(), code.getTrxCode(), code.getCpi(), code.getSize(), code.getRequestId(),
                code.getCreatedBy(),
                code.getCreatedAt() == null ? null : code.getCreatedAt().atZone(ZoneId.systemDefault()).toOffsetDateTime(),
                "/api/v1/admin/shortcodes/" + code.getShortcodeUuid() + "/qr-codes/" + code.getUuid() + "/image",
                withImage ? code.getImageBase64() : null);
    }
}
