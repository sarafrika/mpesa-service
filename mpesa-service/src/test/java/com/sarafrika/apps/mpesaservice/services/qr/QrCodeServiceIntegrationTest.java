package com.sarafrika.apps.mpesaservice.services.qr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sarafrika.apps.mpesaservice.config.ClientIpResolver;
import com.sarafrika.apps.mpesaservice.dtos.MpesaApiResponse;
import com.sarafrika.apps.mpesaservice.dtos.MpesaErrorResponse;
import com.sarafrika.apps.mpesaservice.dtos.QRCodeResponse;
import com.sarafrika.apps.mpesaservice.dtos.admin.qr.CreateQrCodeRequest;
import com.sarafrika.apps.mpesaservice.dtos.admin.qr.QrCodeDto;
import com.sarafrika.apps.mpesaservice.exceptions.ConflictException;
import com.sarafrika.apps.mpesaservice.exceptions.InvalidRequestException;
import com.sarafrika.apps.mpesaservice.exceptions.UpstreamException;
import com.sarafrika.apps.mpesaservice.models.MpesaShortCode;
import com.sarafrika.apps.mpesaservice.repositories.MpesaShortCodeRepository;
import com.sarafrika.apps.mpesaservice.services.MpesaDarajaService;
import com.sarafrika.apps.mpesaservice.services.audit.AuditService;
import com.sarafrika.apps.mpesaservice.services.security.SecretCipher;
import com.sarafrika.apps.mpesaservice.utils.PageRequests;
import com.sarafrika.apps.mpesaservice.utils.enums.Environment;
import com.sarafrika.apps.mpesaservice.utils.enums.QRTransactionType;
import com.sarafrika.apps.mpesaservice.utils.enums.ShortcodeType;
import java.math.BigDecimal;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Dynamic QR codes are generated through Daraja, stored with their PNG on the real migrations, and audited. */
@DataJpaTest(properties = {
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migrations",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect"})
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({QrCodeService.class, SecretCipher.class, AuditService.class, QrCodeServiceIntegrationTest.Beans.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class QrCodeServiceIntegrationTest {

    // The first bytes of a real PNG (signature + IHDR), enough for the signature check.
    static final String PNG = Base64.getEncoder().encodeToString(new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10,
            0, 0, 0, 13, 'I', 'H', 'D', 'R'});

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @TestConfiguration
    static class Beans {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }

        @Bean
        ClientIpResolver clientIpResolver() {
            return new ClientIpResolver(List.of());
        }
    }

    @Autowired
    private QrCodeService service;
    @Autowired
    private MpesaShortCodeRepository shortCodeRepository;
    @Autowired
    private JdbcTemplate jdbc;
    @MockitoBean
    private MpesaDarajaService darajaService;

    private MpesaShortCode paybill;

    @BeforeEach
    void setUp() {
        paybill = shortCodeRepository.saveAndFlush(code(ShortcodeType.PAYBILL));
    }

    @Test
    void generatesStoresListsAndServesThePng() {
        when(darajaService.generateDynamicQRCode(eq(paybill.getUuid()), eq("Elimika Fees"), eq("ELM-004512"),
                any(), eq(QRTransactionType.PAY_BILL), eq(300)))
                .thenReturn(MpesaApiResponse.success(new QRCodeResponse("AG_20191219_000043fdf61864fe9ff5",
                        "16738-27456357-1", "QR Code Successfully Generated.", PNG), 200));

        QrCodeDto created = service.create(paybill.getUuid(),
                new CreateQrCodeRequest(" Elimika Fees ", "ELM-004512", new BigDecimal("1500"), null, null));

        assertThat(created.trxCode()).isEqualTo("PB");
        assertThat(created.cpi()).isEqualTo(paybill.getShortcode());
        assertThat(created.imageBase64()).isEqualTo(PNG);
        assertThat(created.requestId()).isEqualTo("16738-27456357-1");
        assertThat(created.imagePath()).endsWith("/qr-codes/" + created.uuid() + "/image");

        var page = service.list(paybill.getUuid(), PageRequests.of(0, 25, null, Map.of(),
                Sort.by(Sort.Order.desc("createdAt"))));
        assertThat(page.getContent()).singleElement().satisfies(row -> assertThat(row.imageBase64()).isNull());

        QrCodeService.Image image = service.image(paybill.getUuid(), created.uuid());
        assertThat(image.png()).isEqualTo(Base64.getDecoder().decode(PNG));
        assertThat(image.fileName()).isEqualTo("qr-" + paybill.getShortcode() + "-ELM-004512.png");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_events WHERE action = 'QR_CODE_CREATED' "
                + "AND entity_id = ?", Long.class, created.uuid().toString())).isEqualTo(1);
    }

    @Test
    void darajaFailuresBecome502Or503() {
        CreateQrCodeRequest request = new CreateQrCodeRequest("Shop", "INV-1", null, "PB", 300);
        when(darajaService.generateDynamicQRCode(any(), any(), any(), any(), any(), any()))
                .thenReturn(MpesaApiResponse.error(MpesaErrorResponse.NETWORK_ERROR, "timed out", 503));
        assertThatThrownBy(() -> service.create(paybill.getUuid(), request)).isInstanceOfSatisfying(
                UpstreamException.class, e -> assertThat(e.isUnavailable()).isTrue());

        when(darajaService.generateDynamicQRCode(any(), any(), any(), any(), any(), any()))
                .thenReturn(MpesaApiResponse.error("400.002.02", "Bad Request - Invalid Amount", 400));
        assertThatThrownBy(() -> service.create(paybill.getUuid(), request)).isInstanceOfSatisfying(
                UpstreamException.class, e -> {
                    assertThat(e.isUnavailable()).isFalse();
                    assertThat(e.getMessage()).contains("Invalid Amount");
                });

        when(darajaService.generateDynamicQRCode(any(), any(), any(), any(), any(), any()))
                .thenReturn(MpesaApiResponse.success(new QRCodeResponse("1", null, "Failed", null), 200));
        assertThatThrownBy(() -> service.create(paybill.getUuid(), request)).isInstanceOf(UpstreamException.class)
                .hasMessageContaining("without a QR image");
    }

    @Test
    void theTrxCodeMustFitTheShortcodeAndB2COrSuspendedCodesAreRefused() {
        assertThatThrownBy(() -> service.create(paybill.getUuid(),
                new CreateQrCodeRequest("Shop", "INV-1", null, "BG", null))).isInstanceOf(InvalidRequestException.class);

        MpesaShortCode b2c = shortCodeRepository.saveAndFlush(code(ShortcodeType.B2C));
        assertThatThrownBy(() -> service.create(b2c.getUuid(),
                new CreateQrCodeRequest("Shop", "INV-1", null, null, null))).isInstanceOf(ConflictException.class);

        MpesaShortCode suspended = code(ShortcodeType.TILL);
        suspended.setIsActive(false);
        MpesaShortCode savedSuspended = shortCodeRepository.saveAndFlush(suspended);
        assertThatThrownBy(() -> service.create(savedSuspended.getUuid(),
                new CreateQrCodeRequest("Shop", "INV-1", null, null, null))).isInstanceOf(ConflictException.class);
        verify(darajaService, never()).generateDynamicQRCode(any(), any(), any(), any(), any(), any());
    }

    private static MpesaShortCode code(ShortcodeType type) {
        MpesaShortCode code = new MpesaShortCode();
        code.setShortcode(String.valueOf(600000 + (int) (Math.random() * 399999)));
        code.setShortcodeType(type);
        code.setBusinessName("Elimika Fees");
        code.setConsumerKey("key");
        code.setConsumerSecret("secret");
        code.setCallbackUrl("https://example.test/api/v1/callbacks/stk/callback");
        code.setEnvironment(Environment.SANDBOX);
        code.setInitiatorName("elimika-api");
        return code;
    }
}
