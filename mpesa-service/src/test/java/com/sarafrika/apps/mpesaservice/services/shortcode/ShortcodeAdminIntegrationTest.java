package com.sarafrika.apps.mpesaservice.services.shortcode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sarafrika.apps.mpesaservice.config.ClientIpResolver;
import com.sarafrika.apps.mpesaservice.dtos.C2BRegistrationResponse;
import com.sarafrika.apps.mpesaservice.dtos.MpesaApiResponse;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.C2bRegistrationDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.C2bRegistrationState;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.C2bResponseType;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.ConnectionTestResultDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.CreateShortcodeRequest;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.RotateShortcodeCredentialsRequest;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.ShortcodeDetailDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.ShortcodeStatus;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.ShortcodeSummaryDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.SimulateC2bRequest;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.UpdateShortcodeRequest;
import com.sarafrika.apps.mpesaservice.exceptions.ConflictException;
import com.sarafrika.apps.mpesaservice.exceptions.InvalidRequestException;
import com.sarafrika.apps.mpesaservice.exceptions.UpstreamException;
import com.sarafrika.apps.mpesaservice.services.MpesaDarajaService;
import com.sarafrika.apps.mpesaservice.services.audit.AuditService;
import com.sarafrika.apps.mpesaservice.services.security.SecretCipher;
import com.sarafrika.apps.mpesaservice.utils.enums.Environment;
import com.sarafrika.apps.mpesaservice.utils.enums.ShortcodeType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Shortcode onboarding, health and lifecycle on the real migrations (V202610270201/02) with Hibernate validate. */
@DataJpaTest(properties = {
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migrations",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "mpesa.callback.base-url=https://pay.example.test/api/v1/callbacks"})
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ShortcodeAdminService.class, ShortcodeC2bService.class, ShortcodeHealthReader.class, CallbackUrls.class,
        AuditService.class, SecretCipher.class, ShortcodeAdminIntegrationTest.Beans.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ShortcodeAdminIntegrationTest {

    private static final AtomicInteger NEXT = new AtomicInteger(700000);

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private ShortcodeAdminService service;
    @Autowired
    private ShortcodeC2bService c2b;
    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private ShortcodeConnectionTester tester;
    @MockitoBean
    private MpesaDarajaService daraja;

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

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void aNewShortcodeIsAnEncryptedDraftWithMaskedCredentialsAndAnAuditEntry() {
        signIn("mpesa-operator");
        String number = next();

        ShortcodeDetailDto created = service.create(request(number, ShortcodeType.PAYBILL, Environment.SANDBOX));

        assertThat(created.status()).isEqualTo(ShortcodeStatus.DRAFT);
        assertThat(created.active()).isFalse();
        assertThat(created.credentials().consumerKeyMasked()).isEqualTo("••••Qx7d");
        assertThat(created.credentials().consumerSecretMasked()).isEqualTo("••••9KfA");
        assertThat(created.credentials().rotationDue()).isFalse();
        assertThat(created.callbackUrl()).isEqualTo("https://pay.example.test/api/v1/callbacks/stk/callback");
        assertThat(created.createdBy()).isEqualTo("jane.doe");
        assertThat(jdbc.queryForObject("SELECT consumer_secret FROM mpesa_shortcodes WHERE shortcode = ?",
                String.class, number)).startsWith(SecretCipher.PREFIX);
        String audit = jdbc.queryForObject("SELECT after::text FROM audit_events WHERE entity_id = ? "
                + "AND action = 'SHORTCODE_CREATED'", String.class, created.uuid().toString());
        assertThat(audit).contains(number).doesNotContain("secret-9KfA").doesNotContain("key-Qx7d");
    }

    @Test
    void theMigrationAcceptsB2cShortcodesAndOnlyKnownResponseTypes() {
        signIn("mpesa-operator");
        ShortcodeDetailDto b2c = service.create(new CreateShortcodeRequest(next(), ShortcodeType.B2C, "Payouts",
                Environment.SANDBOX, "key-Qx7d", "secret-9KfA", null, "elimika-api", "pw", null, null, null,
                null, null));

        assertThat(b2c.shortcodeType()).isEqualTo(ShortcodeType.B2C);
        assertThat(b2c.health().c2bState()).isEqualTo(C2bRegistrationState.NOT_APPLICABLE);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO c2b_registrations (shortcode_uuid, response_type, "
                + "confirmation_url, validation_url, successful, registered_by) VALUES (?, 'Maybe', 'a', 'b', true, 'x')",
                b2c.uuid())).hasMessageContaining("c2b_registrations_response_type_check");
    }

    @Test
    void duplicatesBadLimitsAndIncompleteB2cShortcodesAreRefused() {
        signIn("mpesa-operator");
        String number = next();
        service.create(request(number, ShortcodeType.PAYBILL, Environment.SANDBOX));

        assertThatThrownBy(() -> service.create(request(number, ShortcodeType.TILL, Environment.SANDBOX)))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.create(new CreateShortcodeRequest(next(), ShortcodeType.B2C, "Payouts",
                Environment.SANDBOX, "k", "s", null, null, null, null, null, null, null, null)))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("B2C");
        assertThatThrownBy(() -> service.create(new CreateShortcodeRequest(next(), ShortcodeType.PAYBILL, "Fees",
                Environment.SANDBOX, "k", "s", null, null, null, null, new BigDecimal("500"), new BigDecimal("10"),
                null, null)))
                .isInstanceOfSatisfying(InvalidRequestException.class, e -> assertThat(e.getField()).isEqualTo("max_amount"));
    }

    @Test
    void onlyAnAdminMayAddAProductionShortcode() {
        signIn("mpesa-operator");
        assertThatThrownBy(() -> service.create(request(next(), ShortcodeType.PAYBILL, Environment.PRODUCTION)))
                .isInstanceOf(AccessDeniedException.class);

        signIn("mpesa-admin");
        assertThat(service.create(request(next(), ShortcodeType.PAYBILL, Environment.PRODUCTION)).environment())
                .isEqualTo(Environment.PRODUCTION);
    }

    @Test
    void theListFiltersSearchesAndCarriesEachShortcodesHealth() {
        signIn("mpesa-admin");
        String tag = "Health" + next();
        ShortcodeDetailDto fees = service.create(named(tag + " Fees", ShortcodeType.PAYBILL));
        ShortcodeDetailDto shop = service.create(named(tag + " Shop", ShortcodeType.TILL));
        payment(fees.uuid(), "SUCCESS", "1500.00", LocalDateTime.now());
        payment(fees.uuid(), "SUCCESS", "250.50", LocalDateTime.now());
        payment(fees.uuid(), "PENDING", "999.00", LocalDateTime.now());
        payment(fees.uuid(), "SUCCESS", "777.00", LocalDateTime.now().minusDays(2));
        payout(fees.uuid(), "98500.00");
        registration(fees.uuid(), false);
        registration(fees.uuid(), true);

        Page<ShortcodeSummaryDto> all = service.list(query(null, tag), PageRequest.of(0, 25,
                Sort.by(Sort.Order.asc("businessName"), Sort.Order.desc("id"))));
        Page<ShortcodeSummaryDto> tills = service.list(new ShortcodeAdminService.ShortcodeQuery(
                ShortcodeType.TILL, null, null, null, tag.toLowerCase()), PageRequest.of(0, 25));
        Page<ShortcodeSummaryDto> pastTheEnd = service.list(query(null, tag), PageRequest.of(5, 25));

        assertThat(all.getContent()).extracting(ShortcodeSummaryDto::uuid).containsExactly(fees.uuid(), shop.uuid());
        ShortcodeSummaryDto row = all.getContent().getFirst();
        assertThat(row.health().collectedToday()).isEqualByComparingTo("1750.50");
        assertThat(row.health().lastPaymentAt()).isNotNull();
        assertThat(row.health().floatUtility()).isEqualByComparingTo("98500.00");
        assertThat(row.health().c2bState()).isEqualTo(C2bRegistrationState.REGISTERED);
        assertThat(all.getContent().get(1).health().c2bState()).isEqualTo(C2bRegistrationState.NOT_REGISTERED);
        assertThat(all.getContent().get(1).health().collectedToday()).isEqualByComparingTo("0");
        assertThat(tills.getContent()).extracting(ShortcodeSummaryDto::uuid).containsExactly(shop.uuid());
        assertThat(pastTheEnd.getContent()).isEmpty();
        assertThat(pastTheEnd.getTotalElements()).isEqualTo(2);
        assertThat(service.list(query(ShortcodeStatus.ACTIVE, tag), PageRequest.of(0, 25)).getContent()).isEmpty();
        assertThat(service.list(query(null, fees.shortcode()), PageRequest.of(0, 25)).getContent()).hasSize(1);
        assertThat(service.totals().totalFloat()).isGreaterThanOrEqualTo(new BigDecimal("98500.00"));
    }

    @Test
    void aDraftGoesLiveOnlyAfterAPassingTokenCheckAndCanBeSuspendedAndResumed() {
        signIn("mpesa-admin");
        ShortcodeDetailDto draft = service.create(request(next(), ShortcodeType.PAYBILL, Environment.SANDBOX));
        assertThatThrownBy(() -> service.activate(draft.uuid())).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.suspend(draft.uuid(), null)).isInstanceOf(ConflictException.class);

        when(tester.test(any())).thenReturn(new ConnectionTestResultDto(true, 312, null, LocalDateTime.now()));
        ConnectionTestResultDto check = service.testConnection(draft.uuid());
        ShortcodeDetailDto active = service.activate(draft.uuid());
        ShortcodeDetailDto suspended = service.suspend(draft.uuid(), "Rotating keys");
        ShortcodeDetailDto resumed = service.activate(draft.uuid());

        assertThat(check.ok()).isTrue();
        assertThat(active.status()).isEqualTo(ShortcodeStatus.ACTIVE);
        assertThat(active.health().tokenOk()).isTrue();
        assertThat(active.health().tokenLatencyMs()).isEqualTo(312);
        assertThat(suspended.status()).isEqualTo(ShortcodeStatus.SUSPENDED);
        assertThat(resumed.status()).isEqualTo(ShortcodeStatus.ACTIVE);
        assertThatThrownBy(() -> service.activate(draft.uuid())).isInstanceOf(ConflictException.class);
        assertThat(jdbc.queryForList("SELECT action FROM audit_events WHERE entity_id = ? ORDER BY id",
                String.class, draft.uuid().toString())).containsExactly("SHORTCODE_CREATED",
                "SHORTCODE_CONNECTION_TESTED", "SHORTCODE_ACTIVATED", "SHORTCODE_SUSPENDED", "SHORTCODE_ACTIVATED");
    }

    @Test
    void aFailedTokenCheckIsRecordedNotThrown() {
        signIn("mpesa-operator");
        ShortcodeDetailDto draft = service.create(request(next(), ShortcodeType.PAYBILL, Environment.SANDBOX));
        when(tester.test(any())).thenReturn(new ConnectionTestResultDto(false, 40,
                "Daraja refused these credentials (400 BAD_REQUEST)", LocalDateTime.now()));

        ConnectionTestResultDto result = service.testConnection(draft.uuid());

        assertThat(result.ok()).isFalse();
        assertThat(service.get(draft.uuid()).health().tokenError()).contains("refused");
    }

    @Test
    void updatesChangeOnlyWhatIsGivenAndRotationResetsTheTokenCheck() {
        signIn("mpesa-admin");
        ShortcodeDetailDto draft = service.create(request(next(), ShortcodeType.PAYBILL, Environment.SANDBOX));
        when(tester.test(any())).thenReturn(new ConnectionTestResultDto(true, 10, null, LocalDateTime.now()));
        service.testConnection(draft.uuid());

        ShortcodeDetailDto updated = service.update(draft.uuid(), new UpdateShortcodeRequest("Renamed", null,
                new BigDecimal("250000"), "", null, null, null, null));
        ShortcodeDetailDto rotated = service.rotateCredentials(draft.uuid(),
                new RotateShortcodeCredentialsRequest(null, "new-secret-ZZ99", "", null, null, null));

        assertThat(updated.businessName()).isEqualTo("Renamed");
        assertThat(updated.maxAmount()).isEqualByComparingTo("250000");
        assertThat(updated.accountReference()).isNull();
        assertThat(updated.transactionDesc()).isEqualTo("Elimika fees");
        assertThat(rotated.credentials().consumerSecretMasked()).isEqualTo("••••ZZ99");
        assertThat(rotated.credentials().consumerKeyMasked()).isEqualTo("••••Qx7d");
        assertThat(rotated.credentials().passkeyMasked()).isEqualTo("••••b21c");
        assertThat(rotated.health().tokenOk()).isNull();
        verify(daraja).evictAccessToken(draft.uuid());
        String audit = jdbc.queryForObject("SELECT after::text FROM audit_events WHERE entity_id = ? "
                + "AND action = 'SHORTCODE_CREDENTIALS_ROTATED'", String.class, draft.uuid().toString());
        assertThat(audit).contains("consumer_secret").contains("[REDACTED]").doesNotContain("new-secret-ZZ99");
        assertThatThrownBy(() -> service.rotateCredentials(draft.uuid(),
                new RotateShortcodeCredentialsRequest(" ", null, null, null, null, null)))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void everyC2bRegistrationAttemptIsKeptAndAFailureAnswersUpstream() {
        signIn("mpesa-operator");
        ShortcodeDetailDto code = service.create(request(next(), ShortcodeType.PAYBILL, Environment.SANDBOX));
        when(daraja.registerC2BUrls(eq(code.uuid()), anyString(), anyString(), eq("Cancelled")))
                .thenReturn(MpesaApiResponse.error("CLIENT_ERROR", "Client error: 400 BAD_REQUEST", 400))
                .thenReturn(MpesaApiResponse.success(new C2BRegistrationResponse(null, "orig", "Success"), 200));

        assertThatThrownBy(() -> c2b.register(code.uuid(), C2bResponseType.Cancelled))
                .isInstanceOfSatisfying(UpstreamException.class, e -> assertThat(e.isUnavailable()).isFalse());
        C2bRegistrationDto registered = c2b.register(code.uuid(), C2bResponseType.Cancelled);
        Page<C2bRegistrationDto> history = c2b.history(code.uuid(), PageRequest.of(0, 25,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));

        assertThat(registered.successful()).isTrue();
        assertThat(registered.confirmationUrl()).isEqualTo("https://pay.example.test/api/v1/callbacks/c2b/confirmation");
        assertThat(registered.registeredBy()).isEqualTo("jane.doe");
        assertThat(history.getContent()).extracting(C2bRegistrationDto::successful).containsExactly(true, false);
        ShortcodeDetailDto after = service.get(code.uuid());
        assertThat(after.health().c2bState()).isEqualTo(C2bRegistrationState.REGISTERED);
        assertThat(after.validationUrl()).isEqualTo("https://pay.example.test/api/v1/callbacks/c2b/validation");
    }

    @Test
    void anUnreachableSafaricomAnswers503AndSimulationIsSandboxOnly() {
        signIn("mpesa-admin");
        ShortcodeDetailDto code = service.create(request(next(), ShortcodeType.PAYBILL, Environment.PRODUCTION));
        when(daraja.registerC2BUrls(eq(code.uuid()), anyString(), anyString(), anyString()))
                .thenReturn(MpesaApiResponse.error("INTERNAL_ERROR", "I/O error on POST", 500));

        assertThatThrownBy(() -> c2b.register(code.uuid(), C2bResponseType.Completed))
                .isInstanceOfSatisfying(UpstreamException.class, e -> assertThat(e.isUnavailable()).isTrue());
        assertThat(service.get(code.uuid()).health().c2bState()).isEqualTo(C2bRegistrationState.FAILED);
        assertThatThrownBy(() -> c2b.simulate(code.uuid(),
                new SimulateC2bRequest("254708374149", BigDecimal.TEN, "ELM-1")))
                .isInstanceOf(ConflictException.class);
    }

    private static String next() {
        return String.valueOf(NEXT.incrementAndGet());
    }

    private static CreateShortcodeRequest request(String number, ShortcodeType type, Environment environment) {
        return new CreateShortcodeRequest(number, type, "Elimika Fees " + number, environment, "key-Qx7d",
                "secret-9KfA", "passkey-b21c", null, null, null, new BigDecimal("10"), new BigDecimal("150000"),
                "ELIMIKA", "Elimika fees");
    }

    private static CreateShortcodeRequest named(String name, ShortcodeType type) {
        return new CreateShortcodeRequest(next(), type, name, Environment.SANDBOX, "key-Qx7d", "secret-9KfA",
                "passkey-b21c", null, null, null, null, null, null, null);
    }

    private static ShortcodeAdminService.ShortcodeQuery query(ShortcodeStatus status, String q) {
        return new ShortcodeAdminService.ShortcodeQuery(null, null, null, status, q);
    }

    private void payment(UUID shortcode, String status, String amount, LocalDateTime at) {
        jdbc.update("INSERT INTO mpesa_incoming_payments (shortcode_uuid, payment_type, transaction_id, phone_number, "
                + "amount, status, created_at) VALUES (?, 'C2B', ?, '254712345678', ?, ?, ?)",
                shortcode, "T" + UUID.randomUUID().toString().substring(0, 12), new BigDecimal(amount), status, at);
    }

    private void payout(UUID shortcode, String utility) {
        jdbc.update("INSERT INTO mpesa_outgoing_payments (shortcode_uuid, payment_type, originator_conversation_id, "
                + "recipient_phone_number, amount, command_id, initiator_name, status, utility_account_available_funds, "
                + "processed_at) VALUES (?, 'B2C', ?, '254712345678', 100, 'BusinessPayment', 'api', 'SUCCESS', ?, NOW())",
                shortcode, "sar-" + UUID.randomUUID(), new BigDecimal(utility));
    }

    private void registration(UUID shortcode, boolean successful) {
        jdbc.update("INSERT INTO c2b_registrations (shortcode_uuid, response_type, confirmation_url, validation_url, "
                + "successful, registered_by) VALUES (?, 'Completed', 'https://c', 'https://v', ?, 'jane.doe')",
                shortcode, successful);
    }

    private static void signIn(String role) {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject("sub-1")
                .claim("preferred_username", "jane.doe").build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }
}
