package com.sarafrika.apps.mpesaservice.services.shortcode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sarafrika.apps.mpesaservice.config.ClientIpResolver;
import com.sarafrika.apps.mpesaservice.dtos.C2BRegistrationResponse;
import com.sarafrika.apps.mpesaservice.dtos.MpesaApiResponse;
import com.sarafrika.apps.mpesaservice.dtos.StkPushResponse;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.C2bRegistrationDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.C2bRegistrationState;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.C2bResponseType;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.ConnectionTestResultDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.CreateShortcodeRequest;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.UpdateShortcodeCredentialsRequest;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.ShortcodeDetailDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.ShortcodeHealthDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.ShortcodeStatus;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.ShortcodeSummaryDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.ShortcodeTotalsDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.SimulateC2bRequest;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.TestStkRequest;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.TestStkResultDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.UpdateShortcodeRequest;
import com.sarafrika.apps.mpesaservice.exceptions.CapabilityNotSupportedException;
import com.sarafrika.apps.mpesaservice.exceptions.ConflictException;
import com.sarafrika.apps.mpesaservice.models.MpesaShortCode;
import com.sarafrika.apps.mpesaservice.utils.enums.ShortcodeCapability;
import com.sarafrika.apps.mpesaservice.exceptions.InvalidRequestException;
import com.sarafrika.apps.mpesaservice.exceptions.UpstreamException;
import com.sarafrika.apps.mpesaservice.services.MpesaDarajaService;
import com.sarafrika.apps.mpesaservice.services.audit.AuditService;
import com.sarafrika.apps.mpesaservice.services.payout.SecurityCredentialService;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.UpdateShortcodeOperationsRequest;
import com.sarafrika.apps.mpesaservice.utils.enums.ShortcodeOperation;
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
        "mpesa.callback.base-url=https://pay.example.test/api/v1/callbacks",
        "mpesa.certificates.sandbox=test-sandbox-certificate",
        "mpesa.certificates.production="})
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ShortcodeAdminService.class, ShortcodeC2bService.class, ShortcodeHealthReader.class, CallbackUrls.class,
        com.sarafrika.apps.mpesaservice.services.callbacks.CallbackFailureCounts.class,
        AuditService.class, SecretCipher.class, SecurityCredentialService.class,
        ShortcodeAdminIntegrationTest.Beans.class})
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
    @Autowired
    private com.sarafrika.apps.mpesaservice.repositories.MpesaShortCodeRepository repository;

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
        assertThat(created.credentials().updatedAt()).isNotNull();
        assertThat(created.callbackUrl()).matches("https://pay\\.example\\.test/api/v1/callbacks/••••[0-9a-f]{4}/stk/callback");
        String token = jdbc.queryForObject("SELECT callback_token FROM mpesa_shortcodes WHERE shortcode = ?",
                String.class, number);
        assertThat(token).matches("[0-9a-f]{64}").endsWith(created.callbackUrl().replaceAll(".*••••([0-9a-f]{4}).*", "$1"));
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
                Environment.SANDBOX, "k", "s", "a-passkey", null, null, null, null, null, null, null)))
                .isInstanceOfSatisfying(InvalidRequestException.class, e -> assertThat(e.getField()).isEqualTo("passkey"));
        assertThatThrownBy(() -> service.create(new CreateShortcodeRequest(next(), ShortcodeType.TILL, "Shop",
                Environment.SANDBOX, "k", "s", null, null, null, null, null, null, null, null)))
                .isInstanceOfSatisfying(InvalidRequestException.class,
                        e -> assertThat(e.getField()).isEqualTo("store_number"));
        assertThatThrownBy(() -> service.create(new CreateShortcodeRequest(next(), ShortcodeType.PAYBILL, "Fees",
                Environment.SANDBOX, "k", "s", null, null, null, null, null, null, null, null, "174300")))
                .isInstanceOfSatisfying(InvalidRequestException.class,
                        e -> assertThat(e.getField()).isEqualTo("store_number"));
        assertThatThrownBy(() -> service.create(new CreateShortcodeRequest(next(), ShortcodeType.PAYBILL, "Fees",
                Environment.SANDBOX, "k", "s", null, null, null, null, new BigDecimal("500"), new BigDecimal("10"),
                null, null)))
                .isInstanceOfSatisfying(InvalidRequestException.class, e -> assertThat(e.getField()).isEqualTo("max_amount"));
    }

    @Test
    void eachTypeCarriesItsCapabilitiesAndATillItsStoreNumber() {
        signIn("mpesa-operator");
        ShortcodeDetailDto till = service.create(new CreateShortcodeRequest(next(), ShortcodeType.TILL, "Shop",
                Environment.SANDBOX, "k", "s", "pk", null, null, null, null, null, null, null, "1" + next()));
        ShortcodeDetailDto b2c = service.create(new CreateShortcodeRequest(next(), ShortcodeType.B2C, "Payouts",
                Environment.SANDBOX, "k", "s", null, null, null, null, null, null, null, null));

        assertThat(till.storeNumber()).startsWith("1");
        assertThat(till.storeNumberConfirmed()).isTrue();
        assertThat(till.capabilities()).contains(ShortcodeCapability.C2B_REGISTER, ShortcodeCapability.QR)
                .doesNotContain(ShortcodeCapability.STK_PUSH, ShortcodeCapability.B2C_PAYOUT,
                        ShortcodeCapability.B2B_EXPRESS_CHECKOUT);
        assertThat(b2c.capabilities()).containsExactly(ShortcodeCapability.B2C_PAYOUT,
                ShortcodeCapability.ACCOUNT_BALANCE);
        assertThat(b2c.availableOperations()).containsExactly(ShortcodeOperation.TRANSACTION_STATUS,
                ShortcodeOperation.ACCOUNT_BALANCE, ShortcodeOperation.B2C_PAYOUT, ShortcodeOperation.POCHI);
        assertThat(service.list(query(null, b2c.shortcode()), PageRequest.of(0, 25)).getContent().getFirst()
                .capabilities()).contains(ShortcodeCapability.B2C_PAYOUT);
        assertThatThrownBy(() -> c2b.register(b2c.uuid(), C2bResponseType.Completed))
                .isInstanceOf(CapabilityNotSupportedException.class).hasMessageContaining("B2C disbursement");
        assertThatThrownBy(() -> service.update(b2c.uuid(), new UpdateShortcodeRequest(null, null, null, null, null,
                null, null, null, "174300"))).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.updateCredentials(b2c.uuid(),
                new UpdateShortcodeCredentialsRequest(null, null, "pk", null, null, null)))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void theC2bTotalCountsEveryUnsuspendedShortcodeThatCanRegisterUrls() {
        signIn("mpesa-operator");
        ShortcodeTotalsDto before = service.totals();
        ShortcodeDetailDto paybill = service.create(named("Totals Fees", ShortcodeType.PAYBILL));
        service.create(named("Totals Shop", ShortcodeType.TILL));
        service.create(new CreateShortcodeRequest(next(), ShortcodeType.B2C, "Totals Payouts",
                Environment.SANDBOX, "k", "s", null, null, null, null, null, null, null, null));
        ShortcodeDetailDto suspended = service.create(named("Totals Old", ShortcodeType.PAYBILL));
        jdbc.update("UPDATE mpesa_shortcodes SET is_active = false, activated_at = NOW() WHERE uuid = ?",
                suspended.uuid());
        registration(paybill.uuid(), true);

        ShortcodeTotalsDto after = service.totals();

        assertThat(after.c2bApplicable() - before.c2bApplicable()).isEqualTo(2);
        assertThat(after.c2bRegistered() - before.c2bRegistered()).isEqualTo(1);
    }

    @Test
    void aMigratedTillsStoreNumberIsConfirmedByEditingIt() {
        signIn("mpesa-operator");
        String number = next();
        ShortcodeDetailDto till = service.create(new CreateShortcodeRequest(number, ShortcodeType.TILL, "Shop",
                Environment.SANDBOX, "k", "s", "pk", null, null, null, null, null, null, null, number));
        jdbc.update("UPDATE mpesa_shortcodes SET store_number_confirmed_at = NULL WHERE uuid = ?", till.uuid());
        assertThat(service.get(till.uuid()).storeNumberConfirmed()).isFalse();
        assertThat(repository.findTillsWithUnconfirmedStoreNumber()).extracting(MpesaShortCode::getUuid)
                .contains(till.uuid());

        ShortcodeDetailDto confirmed = service.update(till.uuid(), new UpdateShortcodeRequest(null, null, null, null,
                null, null, null, null, number));

        assertThat(confirmed.storeNumberConfirmed()).isTrue();
        assertThat(repository.findTillsWithUnconfirmedStoreNumber()).extracting(MpesaShortCode::getUuid)
                .doesNotContain(till.uuid());
        assertThat(repository.findByNumber(number)).map(MpesaShortCode::getUuid).contains(till.uuid());
    }

    @Test
    void anOperationNeedingTheInitiatorGoesLiveOnlyWithIt() {
        signIn("mpesa-admin");
        when(tester.test(any())).thenReturn(new ConnectionTestResultDto(true, 10, null, LocalDateTime.now()));
        ShortcodeDetailDto draft = service.create(new CreateShortcodeRequest(next(), ShortcodeType.ONE_ACCOUNT,
                "Both", Environment.SANDBOX, "k", "s", "pk", null, null, null, null, null, null, null));
        service.testConnection(draft.uuid());

        assertThatThrownBy(() -> service.activate(draft.uuid())).isInstanceOf(ConflictException.class)
                .hasMessage("Add the API initiator under Credentials first: Account Balance queries need it");
        service.updateCredentials(draft.uuid(), new UpdateShortcodeCredentialsRequest(null, null, null, "api", "pw",
                null));

        assertThat(service.activate(draft.uuid()).status()).isEqualTo(ShortcodeStatus.ACTIVE);
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
        // Backfilled today for money that arrived two days ago, and the other way round.
        payment(fees.uuid(), "SUCCESS", "40.00", LocalDateTime.now(), LocalDateTime.now().minusDays(2));
        payment(fees.uuid(), "SUCCESS", "60.00", LocalDateTime.now().minusDays(2), LocalDateTime.now());
        payout(fees.uuid(), "98500.00");
        failedCallback(fees.uuid(), "FAILED", 1);
        failedCallback(fees.uuid(), "FAILED", 30);
        failedCallback(fees.uuid(), "PROCESSED", 1);
        registration(fees.uuid(), false);
        registration(fees.uuid(), true);

        Page<ShortcodeSummaryDto> all = service.list(query(null, tag), PageRequest.of(0, 25,
                Sort.by(Sort.Order.asc("businessName"), Sort.Order.desc("id"))));
        Page<ShortcodeSummaryDto> tills = service.list(new ShortcodeAdminService.ShortcodeQuery(
                ShortcodeType.TILL, null, null, null, tag.toLowerCase()), PageRequest.of(0, 25));
        Page<ShortcodeSummaryDto> pastTheEnd = service.list(query(null, tag), PageRequest.of(5, 25));

        assertThat(all.getContent()).extracting(ShortcodeSummaryDto::uuid).containsExactly(fees.uuid(), shop.uuid());
        ShortcodeSummaryDto row = all.getContent().getFirst();
        assertThat(row.health().collectedToday()).isEqualByComparingTo("1810.50");
        assertThat(row.health().callbackFailures24h()).isEqualTo(1);
        assertThat(all.getContent().get(1).health().callbackFailures24h()).isZero();
        assertThat(row.health().lastPaymentAt()).isNotNull();
        assertThat(row.health().lastPayoutAt()).isNotNull();
        assertThat(all.getContent().get(1).health().lastPayoutAt()).isNull();
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
    void updatesChangeOnlyWhatIsGivenAndSavingCredentialsRerunsTheTokenCheck() {
        signIn("mpesa-admin");
        ShortcodeDetailDto draft = service.create(request(next(), ShortcodeType.PAYBILL, Environment.SANDBOX));
        when(tester.test(any())).thenReturn(new ConnectionTestResultDto(true, 10, null, LocalDateTime.now()));
        service.testConnection(draft.uuid());

        ShortcodeDetailDto updated = service.update(draft.uuid(), new UpdateShortcodeRequest("Renamed", null,
                new BigDecimal("250000"), "", null, null, null, null));
        ShortcodeDetailDto rotated = service.updateCredentials(draft.uuid(),
                new UpdateShortcodeCredentialsRequest(null, "new-secret-ZZ99", "", null, null, null));

        assertThat(updated.businessName()).isEqualTo("Renamed");
        assertThat(updated.maxAmount()).isEqualByComparingTo("250000");
        assertThat(updated.accountReference()).isNull();
        assertThat(updated.transactionDesc()).isEqualTo("Elimika fees");
        assertThat(rotated.credentials().consumerSecretMasked()).isEqualTo("••••ZZ99");
        assertThat(rotated.credentials().consumerKeyMasked()).isEqualTo("••••Qx7d");
        assertThat(rotated.credentials().passkeyMasked()).isEqualTo("••••b21c");
        assertThat(rotated.health().tokenOk()).isTrue();
        verify(tester, times(2)).test(any());
        verify(daraja).evictAccessToken(draft.uuid());
        String audit = jdbc.queryForObject("SELECT after::text FROM audit_events WHERE entity_id = ? "
                + "AND action = 'SHORTCODE_CREDENTIALS_UPDATED'", String.class, draft.uuid().toString());
        assertThat(audit).contains("consumer_secret").contains("[REDACTED]").doesNotContain("new-secret-ZZ99");
        assertThatThrownBy(() -> service.updateCredentials(draft.uuid(),
                new UpdateShortcodeCredentialsRequest(" ", null, null, null, null, null)))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void everyC2bRegistrationAttemptIsKeptAndAFailureAnswersUpstream() {
        signIn("mpesa-operator");
        ShortcodeDetailDto code = service.create(request(next(), ShortcodeType.PAYBILL, Environment.SANDBOX));
        String token = jdbc.queryForObject("SELECT callback_token FROM mpesa_shortcodes WHERE uuid = ?",
                String.class, code.uuid());
        String base = "https://pay.example.test/api/v1/callbacks/";
        when(daraja.registerC2BUrls(eq(code.uuid()), eq(base + token + "/c2b/confirmation"),
                eq(base + token + "/c2b/validation"), eq("Cancelled")))
                .thenReturn(MpesaApiResponse.error("CLIENT_ERROR", "Client error: 400 BAD_REQUEST", 400))
                .thenReturn(MpesaApiResponse.success(new C2BRegistrationResponse(null, "orig", "Success"), 200));

        assertThatThrownBy(() -> c2b.register(code.uuid(), C2bResponseType.Cancelled))
                .isInstanceOfSatisfying(UpstreamException.class, e -> assertThat(e.isUnavailable()).isFalse());
        C2bRegistrationDto registered = c2b.register(code.uuid(), C2bResponseType.Cancelled);
        Page<C2bRegistrationDto> history = c2b.history(code.uuid(), PageRequest.of(0, 25,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));

        assertThat(registered.successful()).isTrue();
        String shown = base + "••••" + token.substring(60);
        assertThat(registered.confirmationUrl()).isEqualTo(shown + "/c2b/confirmation");
        assertThat(registered.registeredBy()).isEqualTo("jane.doe");
        assertThat(history.getContent()).extracting(C2bRegistrationDto::successful).containsExactly(true, false);
        ShortcodeDetailDto after = service.get(code.uuid());
        assertThat(after.health().c2bState()).isEqualTo(C2bRegistrationState.REGISTERED);
        assertThat(after.validationUrl()).isEqualTo(shown + "/c2b/validation");
        assertThat(jdbc.queryForObject("SELECT validation_url FROM mpesa_shortcodes WHERE uuid = ?", String.class,
                code.uuid())).isEqualTo(base + token + "/c2b/validation");
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

    @Test
    void aTestStkGoesStraightToDarajaOnlyForActiveShortcodesAndIsAudited() {
        signIn("mpesa-admin");
        ShortcodeDetailDto code = service.create(request(next(), ShortcodeType.PAYBILL, Environment.SANDBOX));
        TestStkRequest oneShilling = new TestStkRequest("254708374149", null);
        assertThatThrownBy(() -> c2b.testStk(code.uuid(), oneShilling)).isInstanceOf(ConflictException.class);

        when(tester.test(any())).thenReturn(new ConnectionTestResultDto(true, 200, null, LocalDateTime.now()));
        service.updateOperations(code.uuid(), operations(ShortcodeOperation.C2B, ShortcodeOperation.STK_PUSH));
        service.testConnection(code.uuid());
        service.activate(code.uuid());
        when(daraja.initiateSTKPush(eq(code.uuid()), eq("254708374149"), eq(BigDecimal.ONE), eq("ELIMIKA"),
                anyString())).thenReturn(MpesaApiResponse.success(
                        new StkPushResponse("m-1", "ws_CO_1", "0", "Accepted", "Check your phone"), 200, 5L));

        TestStkResultDto sent = c2b.testStk(code.uuid(), oneShilling);

        assertThat(sent.checkoutRequestId()).isEqualTo("ws_CO_1");
        assertThat(jdbc.queryForObject("SELECT after::text FROM audit_events WHERE entity_id = ? "
                + "AND action = 'SHORTCODE_TEST_STK_SENT'", String.class, code.uuid().toString())).contains("ws_CO_1");
        when(daraja.initiateSTKPush(any(), anyString(), any(), anyString(), anyString()))
                .thenReturn(MpesaApiResponse.error("INVALID_REQUEST", "Bad phone", 400));
        assertThatThrownBy(() -> c2b.testStk(code.uuid(), oneShilling)).isInstanceOf(UpstreamException.class);
    }

    @Test
    void theListNamesEachShortcodesProductsAndFiltersByProduct() {
        signIn("mpesa-operator");
        String tag = "Prod" + next();
        ShortcodeDetailDto fees = service.create(named(tag + " Fees", ShortcodeType.PAYBILL));
        ShortcodeDetailDto shop = service.create(named(tag + " Shop", ShortcodeType.TILL));
        grant("elimika-backend", "Elimika", fees.uuid(), true);
        grant("zz-shop", null, fees.uuid(), true);
        grant("elimika-backend", "Elimika", shop.uuid(), false);

        List<ShortcodeSummaryDto> rows = service.list(query(null, tag), PageRequest.of(0, 25,
                Sort.by("businessName"))).getContent();
        List<ShortcodeSummaryDto> forElimika = service.list(new ShortcodeAdminService.ShortcodeQuery(null, null, null,
                null, tag, "elimika-backend"), PageRequest.of(0, 25)).getContent();

        assertThat(rows.getFirst().products()).containsExactly(
                new ShortcodeSummaryDto.ProductRef("elimika-backend", "Elimika"),
                new ShortcodeSummaryDto.ProductRef("zz-shop", null));
        assertThat(rows.get(1).products()).isEmpty();
        assertThat(forElimika).extracting(ShortcodeSummaryDto::uuid).containsExactly(fees.uuid());
    }

    @Test
    void anInvalidInitiatorResultAsksForNewCredentialsAndTodaysPayoutsAreSummed() {
        signIn("mpesa-operator");
        ShortcodeDetailDto code = service.create(named("Payouts " + next(), ShortcodeType.PAYBILL));
        payout(code.uuid(), "1000.00");
        jdbc.update("INSERT INTO mpesa_outgoing_payments (shortcode_uuid, payment_type, originator_conversation_id, "
                + "recipient_phone_number, amount, command_id, initiator_name, status, result_code, processed_at) "
                + "VALUES (?, 'B2C', ?, '254712345678', 50, 'BusinessPayment', 'api', 'FAILED', 2001, NOW())",
                code.uuid(), "sar-" + UUID.randomUUID());

        ShortcodeDetailDto after = service.get(code.uuid());

        assertThat(after.health().credentialsHint()).contains("Update the credentials");
        assertThat(after.health().paidOutToday()).isEqualByComparingTo("100");
        assertThat(service.totals().initiatorErrors24h()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void aNewerC2bOrgAccountBalanceIsTheUtilityFloatAndKeepsThePayoutsWorkingBalance() {
        signIn("mpesa-operator");
        LocalDateTime now = LocalDateTime.now().withNano(0);
        ShortcodeDetailDto collects = service.create(named("Collects " + next(), ShortcodeType.PAYBILL));
        ShortcodeDetailDto pays = service.create(named("Pays " + next(), ShortcodeType.PAYBILL));
        payout(collects.uuid(), "70000.00", "5000.00", now.minusHours(2));
        c2bBalance(collects.uuid(), "60000.00", now.minusHours(3));
        c2bBalance(collects.uuid(), "71500.00", now.minusHours(1));
        c2bBalance(pays.uuid(), "10.00", now.minusHours(2));
        payout(pays.uuid(), "20000.00", "300.00", now.minusHours(2));

        ShortcodeHealthDto c2b = service.get(collects.uuid()).health();
        ShortcodeHealthDto payout = service.get(pays.uuid()).health();

        assertThat(c2b.floatUtility()).isEqualByComparingTo("71500.00");
        assertThat(c2b.floatWorking()).isEqualByComparingTo("5000.00");
        assertThat(c2b.floatReportedAt()).isEqualTo(now.minusHours(1));
        // A tie goes to the payout, which also reports the working balance.
        assertThat(payout.floatUtility()).isEqualByComparingTo("20000.00");
        assertThat(payout.floatWorking()).isEqualByComparingTo("300.00");
    }

    @Test
    void onlyAnAdminRepointsAProductionShortcodesCallbacks() {
        signIn("mpesa-admin");
        ShortcodeDetailDto live = service.create(request(next(), ShortcodeType.PAYBILL, Environment.PRODUCTION));
        signIn("mpesa-operator");

        assertThatThrownBy(() -> service.update(live.uuid(), new UpdateShortcodeRequest(null, null, null, null, null,
                "https://evil.example/cb", null, null))).isInstanceOf(AccessDeniedException.class);
        assertThat(service.update(live.uuid(), new UpdateShortcodeRequest("Renamed", null, null, null, null,
                live.callbackUrl(), null, null)).businessName()).isEqualTo("Renamed");
        signIn("mpesa-admin");
        assertThat(service.update(live.uuid(), new UpdateShortcodeRequest(null, null, null, null, null,
                "https://pay.example.test/other", null, null)).callbackUrl()).isEqualTo("https://pay.example.test/other");
    }

    @Test
    void eachTypeStartsWithItsDefaultOperationsAndACollectOnlyPaybillGoesLiveOnItsKeysAlone() {
        signIn("mpesa-admin");
        when(tester.test(any())).thenReturn(new ConnectionTestResultDto(true, 10, null, LocalDateTime.now()));
        ShortcodeDetailDto paybill = service.create(new CreateShortcodeRequest(next(), ShortcodeType.PAYBILL, "Fees",
                Environment.PRODUCTION, "k", "s", null, null, null, null, null, null, null, null));
        ShortcodeDetailDto till = service.create(named("Shop", ShortcodeType.TILL));
        ShortcodeDetailDto b2c = service.create(new CreateShortcodeRequest(next(), ShortcodeType.B2C, "Payouts",
                Environment.SANDBOX, "k", "s", null, null, null, null, null, null, null, null));
        ShortcodeDetailDto both = service.create(named("Both", ShortcodeType.ONE_ACCOUNT));

        assertThat(paybill.operations()).containsExactly(ShortcodeOperation.C2B);
        assertThat(till.operations()).containsExactly(ShortcodeOperation.C2B);
        assertThat(b2c.operations()).containsExactly(ShortcodeOperation.ACCOUNT_BALANCE, ShortcodeOperation.B2C_PAYOUT);
        assertThat(both.operations()).containsExactly(ShortcodeOperation.C2B, ShortcodeOperation.ACCOUNT_BALANCE,
                ShortcodeOperation.B2C_PAYOUT);
        assertThat(service.list(query(null, paybill.shortcode()), PageRequest.of(0, 25)).getContent().getFirst()
                .operations()).containsExactly(ShortcodeOperation.C2B);
        service.testConnection(paybill.uuid());
        assertThat(service.activate(paybill.uuid()).status()).isEqualTo(ShortcodeStatus.ACTIVE);
        assertThatThrownBy(() -> service.create(new CreateShortcodeRequest(next(), ShortcodeType.TILL, "Shop",
                Environment.SANDBOX, "k", "s", null, null, null, null, null, null, null, null, "1" + next(),
                List.of(ShortcodeOperation.B2C_PAYOUT))))
                .isInstanceOfSatisfying(InvalidRequestException.class,
                        e -> assertThat(e.getField()).isEqualTo("operations"));
        ShortcodeDetailDto chosen = service.create(new CreateShortcodeRequest(next(), ShortcodeType.PAYBILL, "Fees",
                Environment.SANDBOX, "k", "s", "pk", null, null, null, null, null, null, null, null,
                List.of(ShortcodeOperation.STK_PUSH)));
        assertThat(chosen.operations()).containsExactly(ShortcodeOperation.STK_PUSH);
        assertThat(chosen.capabilities()).contains(ShortcodeCapability.STK_PUSH)
                .doesNotContain(ShortcodeCapability.C2B_REGISTER);
    }

    @Test
    void goingLiveAsksForThePasskeyWhenStkPushIsOn() {
        signIn("mpesa-admin");
        when(tester.test(any())).thenReturn(new ConnectionTestResultDto(true, 10, null, LocalDateTime.now()));
        ShortcodeDetailDto draft = service.create(new CreateShortcodeRequest(next(), ShortcodeType.PAYBILL, "Fees",
                Environment.SANDBOX, "k", "s", null, null, null, null, null, null, null, null, null,
                List.of(ShortcodeOperation.C2B, ShortcodeOperation.STK_PUSH)));
        service.testConnection(draft.uuid());

        assertThatThrownBy(() -> service.activate(draft.uuid())).isInstanceOf(ConflictException.class)
                .hasMessage("Add the passkey under Credentials first: STK push needs it");
        service.updateOperations(draft.uuid(), operations(ShortcodeOperation.C2B));
        assertThat(service.activate(draft.uuid()).status()).isEqualTo(ShortcodeStatus.ACTIVE);
    }

    @Test
    void operationsStayWithinTheTypeAndALiveShortcodeNeedsTheCredentialFirst() {
        signIn("mpesa-admin");
        when(tester.test(any())).thenReturn(new ConnectionTestResultDto(true, 10, null, LocalDateTime.now()));
        ShortcodeDetailDto code = service.create(new CreateShortcodeRequest(next(), ShortcodeType.PAYBILL, "Fees",
                Environment.SANDBOX, "k", "s", null, null, null, null, null, null, null, null));

        assertThatThrownBy(() -> service.updateOperations(code.uuid(), operations(ShortcodeOperation.POCHI)))
                .isInstanceOfSatisfying(InvalidRequestException.class,
                        e -> assertThat(e.getField()).isEqualTo("operations"));
        ShortcodeDetailDto draft = service.updateOperations(code.uuid(),
                operations(ShortcodeOperation.C2B, ShortcodeOperation.REVERSAL));
        assertThat(draft.operations()).containsExactly(ShortcodeOperation.C2B, ShortcodeOperation.REVERSAL);
        service.updateOperations(code.uuid(), operations(ShortcodeOperation.C2B));
        service.testConnection(code.uuid());
        service.activate(code.uuid());

        assertThatThrownBy(() -> service.updateOperations(code.uuid(),
                operations(ShortcodeOperation.C2B, ShortcodeOperation.STK_PUSH)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Add the passkey under Credentials first: STK push needs it");
        assertThatThrownBy(() -> service.updateOperations(code.uuid(), operations(ShortcodeOperation.REVERSAL)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Add the API initiator under Credentials first: reversals need it");
        assertThatThrownBy(() -> c2b.testStk(code.uuid(), new TestStkRequest("254708374149", null)))
                .isInstanceOf(CapabilityNotSupportedException.class)
                .hasMessageContaining("STK push is not turned on for shortcode " + code.shortcode());
        service.updateCredentials(code.uuid(), new UpdateShortcodeCredentialsRequest(null, null, "pk-1234", null,
                null, null));
        ShortcodeDetailDto stk = service.updateOperations(code.uuid(), operations(ShortcodeOperation.STK_PUSH));

        assertThat(stk.operations()).containsExactly(ShortcodeOperation.STK_PUSH);
        assertThat(stk.capabilities()).contains(ShortcodeCapability.STK_PUSH)
                .doesNotContain(ShortcodeCapability.C2B_REGISTER);
        assertThat(stk.health().c2bState()).isEqualTo(C2bRegistrationState.NOT_APPLICABLE);
        assertThat(jdbc.queryForObject("SELECT operations FROM mpesa_shortcodes WHERE uuid = ?", String.class,
                code.uuid())).isEqualTo("STK_PUSH");
        assertThat(jdbc.queryForList("SELECT action FROM audit_events WHERE entity_id = ? "
                + "AND action = 'SHORTCODE_OPERATIONS_UPDATED'", String.class, code.uuid().toString())).hasSize(3);
    }

    @Test
    void anInitiatorPasswordNeedsTheServersCertificateAndStartsTheNinetyDayClock() {
        signIn("mpesa-admin");
        assertThatThrownBy(() -> service.create(new CreateShortcodeRequest(next(), ShortcodeType.B2C, "Payouts",
                Environment.PRODUCTION, "k", "s", null, "api", "pw", null, null, null, null, null)))
                .isInstanceOfSatisfying(InvalidRequestException.class, e -> {
                    assertThat(e.getField()).isEqualTo("initiator_password");
                    assertThat(e.getMessage()).isEqualTo("This server has no Safaricom production certificate, so "
                            + "paste the security credential from the Daraja portal instead");
                });
        ShortcodeDetailDto live = service.create(new CreateShortcodeRequest(next(), ShortcodeType.B2C, "Payouts",
                Environment.PRODUCTION, "k", "s", null, "api", null, "CRED==", null, null, null, null));
        ShortcodeDetailDto sandbox = service.create(new CreateShortcodeRequest(next(), ShortcodeType.B2C, "Payouts",
                Environment.SANDBOX, "k", "s", null, "api", "pw", null, null, null, null, null));
        ShortcodeDetailDto bare = service.create(new CreateShortcodeRequest(next(), ShortcodeType.B2C, "Payouts",
                Environment.SANDBOX, "k", "s", null, null, null, null, null, null, null, null));

        LocalDateTime setAt = jdbc.queryForObject("SELECT initiator_credential_set_at FROM mpesa_shortcodes "
                + "WHERE uuid = ?", LocalDateTime.class, live.uuid());
        assertThat(service.get(live.uuid()).initiatorCredentialExpiresAt()).isEqualTo(setAt.plusDays(90));
        assertThat(sandbox.initiatorCredentialExpiresAt()).isNotNull();
        assertThat(bare.initiatorCredentialExpiresAt()).isNull();
        assertThatThrownBy(() -> service.updateCredentials(live.uuid(),
                new UpdateShortcodeCredentialsRequest(null, null, null, null, "new-pw", null)))
                .isInstanceOfSatisfying(InvalidRequestException.class,
                        e -> assertThat(e.getField()).isEqualTo("initiator_password"));
        when(tester.test(any())).thenReturn(new ConnectionTestResultDto(true, 10, null, LocalDateTime.now()));
        jdbc.update("UPDATE mpesa_shortcodes SET initiator_credential_set_at = NOW() - INTERVAL '60 days' "
                + "WHERE uuid = ?", bare.uuid());
        assertThat(service.get(bare.uuid()).initiatorCredentialExpiresAt()).isNull();
        ShortcodeDetailDto renewed = service.updateCredentials(bare.uuid(),
                new UpdateShortcodeCredentialsRequest(null, null, null, "api", null, "CRED2=="));
        assertThat(renewed.initiatorCredentialExpiresAt()).isAfter(LocalDateTime.now().plusDays(89));
    }

    @Test
    void productionC2bUrlsRegisterOnceUnlessTheOldOnesWereReplaced() {
        signIn("mpesa-admin");
        ShortcodeDetailDto code = service.create(request(next(), ShortcodeType.PAYBILL, Environment.PRODUCTION));
        when(daraja.registerC2BUrls(eq(code.uuid()), anyString(), anyString(), anyString()))
                .thenReturn(MpesaApiResponse.success(new C2BRegistrationResponse(null, "orig", "Success"), 200));
        c2b.register(code.uuid(), C2bResponseType.Completed);

        assertThatThrownBy(() -> c2b.register(code.uuid(), C2bResponseType.Completed))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Safaricom registers production C2B URLs once. Delete the current URLs on the M-PESA org "
                        + "portal (Self Services > URL Management, approved by two operators), then register again.");
        assertThat(c2b.register(code.uuid(), C2bResponseType.Completed, true).successful()).isTrue();
        verify(daraja, times(2)).registerC2BUrls(eq(code.uuid()), anyString(), anyString(), anyString());
        ShortcodeDetailDto sandbox = service.create(request(next(), ShortcodeType.PAYBILL, Environment.SANDBOX));
        when(daraja.registerC2BUrls(eq(sandbox.uuid()), anyString(), anyString(), anyString()))
                .thenReturn(MpesaApiResponse.success(new C2BRegistrationResponse(null, "orig", "Success"), 200));
        c2b.register(sandbox.uuid(), C2bResponseType.Completed);
        assertThat(c2b.register(sandbox.uuid(), C2bResponseType.Completed).successful()).isTrue();
    }

    private static UpdateShortcodeOperationsRequest operations(ShortcodeOperation... operations) {
        return new UpdateShortcodeOperationsRequest(List.of(operations));
    }

    private void grant(String clientId, String name, UUID shortcode, boolean active) {
        jdbc.update("INSERT INTO client_shortcode_grants (client_id, display_name, shortcode_uuid, operations, active) "
                + "VALUES (?, ?, ?, 'STK_PUSH', ?)", clientId, name, shortcode, active);
    }

    private static String next() {
        return String.valueOf(NEXT.incrementAndGet());
    }

    private static CreateShortcodeRequest request(String number, ShortcodeType type, Environment environment) {
        return new CreateShortcodeRequest(number, type, "Elimika Fees " + number, environment, "key-Qx7d",
                "secret-9KfA", "passkey-b21c", null, null, null, new BigDecimal("10"), new BigDecimal("150000"),
                "ELIMIKA", "Elimika fees", storeFor(type));
    }

    private static CreateShortcodeRequest named(String name, ShortcodeType type) {
        return new CreateShortcodeRequest(next(), type, name, Environment.SANDBOX, "key-Qx7d", "secret-9KfA",
                "passkey-b21c", null, null, null, null, null, null, null, storeFor(type));
    }

    private static String storeFor(ShortcodeType type) {
        return type == ShortcodeType.TILL ? "9" + next() : null;
    }

    private static ShortcodeAdminService.ShortcodeQuery query(ShortcodeStatus status, String q) {
        return new ShortcodeAdminService.ShortcodeQuery(null, null, null, status, q);
    }

    private void payment(UUID shortcode, String status, String amount, LocalDateTime createdAt,
                         LocalDateTime transactionDate) {
        jdbc.update("INSERT INTO mpesa_incoming_payments (shortcode_uuid, payment_type, transaction_id, phone_number, "
                + "amount, status, created_at, transaction_date) VALUES (?, 'C2B', ?, '254712345678', ?, ?, ?, ?)",
                shortcode, "T" + UUID.randomUUID().toString().substring(0, 12), new BigDecimal(amount), status,
                createdAt, transactionDate);
    }

    private void payout(UUID shortcode, String utility, String working, LocalDateTime processedAt) {
        jdbc.update("INSERT INTO mpesa_outgoing_payments (shortcode_uuid, payment_type, originator_conversation_id, "
                + "recipient_phone_number, amount, command_id, initiator_name, status, utility_account_available_funds, "
                + "working_account_available_funds, processed_at) "
                + "VALUES (?, 'B2C', ?, '254712345678', 100, 'BusinessPayment', 'api', 'SUCCESS', ?, ?, ?)",
                shortcode, "sar-" + UUID.randomUUID(), new BigDecimal(utility), new BigDecimal(working), processedAt);
    }

    private void c2bBalance(UUID shortcode, String orgBalance, LocalDateTime transactionDate) {
        jdbc.update("INSERT INTO mpesa_incoming_payments (shortcode_uuid, payment_type, transaction_id, phone_number, "
                + "amount, status, org_account_balance, created_at, transaction_date) "
                + "VALUES (?, 'C2B', ?, '254712345678', 10, 'SUCCESS', ?, ?, ?)",
                shortcode, "T" + UUID.randomUUID().toString().substring(0, 12), new BigDecimal(orgBalance),
                transactionDate.plusMinutes(5), transactionDate);
    }

    private void failedCallback(UUID shortcode, String status, int hoursAgo) {
        jdbc.update("INSERT INTO callback_inbox (endpoint, kind, body_size, body_truncated, status, attempts, "
                + "received_at, shortcode_uuid) VALUES ('/api/v1/callbacks/b2b/result', 'B2B_RESULT', 0, false, ?, 1, "
                + "NOW() - make_interval(hours => ?), ?)", status, hoursAgo, shortcode);
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
