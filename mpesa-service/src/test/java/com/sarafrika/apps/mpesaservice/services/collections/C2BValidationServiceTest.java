package com.sarafrika.apps.mpesaservice.services.collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sarafrika.apps.mpesaservice.dtos.C2BValidationRequest;
import com.sarafrika.apps.mpesaservice.models.C2BValidationRule;
import com.sarafrika.apps.mpesaservice.models.MpesaShortCode;
import com.sarafrika.apps.mpesaservice.repositories.C2BValidationRuleRepository;
import com.sarafrika.apps.mpesaservice.repositories.MpesaShortCodeRepository;
import com.sarafrika.apps.mpesaservice.utils.enums.LookupFailurePolicy;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The rules a shortcode sets for C2B validation, including a product lookup that is slow, down or says no. */
class C2BValidationServiceTest {

    private final C2BValidationRuleRepository rules = mock(C2BValidationRuleRepository.class);
    private final MpesaShortCodeRepository shortcodes = mock(MpesaShortCodeRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final C2BValidationService service =
            new C2BValidationService(rules, shortcodes, new AccountLookupClient(objectMapper));

    private HttpServer server;
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String answer = "{\"valid\":true}";
    private volatile long delayMs = 0;
    private MpesaShortCode shortcode;

    @BeforeEach
    void startLookup() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/lookup", exchange -> {
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] bytes = answer.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();

        shortcode = new MpesaShortCode();
        shortcode.setUuid(UUID.randomUUID());
        shortcode.setShortcode("600100");
        when(shortcodes.findByShortcode("600100")).thenReturn(Optional.of(shortcode));
    }

    @AfterEach
    void stopLookup() {
        server.stop(0);
    }

    @Test
    void withoutRulesEveryPaymentIsAccepted() {
        when(rules.findByShortcodeUuid(shortcode.getUuid())).thenReturn(Optional.empty());

        assertThat(service.validate(request("anything", "1")).resultCode()).isEqualTo("0");
    }

    @Test
    void anInactiveRuleAcceptsAndAnUnknownShortcodeIsRejected() {
        C2BValidationRule rule = rule("^ELM-[0-9]{6}$", null, null, null);
        rule.setActive(false);
        when(rules.findByShortcodeUuid(shortcode.getUuid())).thenReturn(Optional.of(rule));

        assertThat(service.validate(request("nope", "10")).accepted()).isTrue();
        C2BValidationRequest elsewhere = new C2BValidationRequest("Pay Bill", "T1", "20261026143208",
                new BigDecimal("10"), "123456", "ELM-000001", null, null, "254712345678", null, null, null);
        assertThat(service.validate(elsewhere).resultCode()).isEqualTo(C2BResultCodes.INVALID_SHORTCODE);
    }

    @Test
    void theAccountPatternAndAmountLimitsHaveTheirOwnCodes() {
        when(rules.findByShortcodeUuid(shortcode.getUuid()))
                .thenReturn(Optional.of(rule("^ELM-[0-9]{6}$", "10", "250000", null)));

        assertThat(service.validate(request("ELM-004512", "4500")).accepted()).isTrue();
        assertThat(service.validate(request("ELM-45", "4500")).resultCode()).isEqualTo(C2BResultCodes.INVALID_ACCOUNT);
        assertThat(service.validate(request(null, "4500")).resultCode()).isEqualTo(C2BResultCodes.INVALID_ACCOUNT);
        assertThat(service.validate(request("ELM-004512", "5")).resultCode()).isEqualTo(C2BResultCodes.INVALID_AMOUNT);
        assertThat(service.validate(request("ELM-004512", "250000.01")).resultCode())
                .isEqualTo(C2BResultCodes.INVALID_AMOUNT);
        C2BValidationRequest noPhone = new C2BValidationRequest("Pay Bill", "T1", null, new BigDecimal("50"),
                "600100", "ELM-004512", null, null, " ", null, null, null);
        assertThat(service.validate(noPhone).resultCode()).isEqualTo(C2BResultCodes.INVALID_MSISDN);
    }

    @Test
    void theProductLookupConfirmsOrRejectsTheAccount() {
        when(rules.findByShortcodeUuid(shortcode.getUuid())).thenReturn(Optional.of(rule(null, null, null, url())));

        assertThat(service.validate(request("ELM-004512", "4500")).accepted()).isTrue();
        assertThat(lastBody.get()).contains("\"account\":\"ELM-004512\"").contains("\"shortcode\":\"600100\"");

        answer = "{\"valid\":false,\"reason\":\"No such student\"}";
        ValidationDecision unknown = service.validate(request("ELM-999999", "4500"));
        assertThat(unknown.resultCode()).isEqualTo(C2BResultCodes.INVALID_ACCOUNT);
        assertThat(unknown.reason()).isEqualTo("No such student");

        answer = "{\"valid\":false,\"result_code\":\"C2B00014\"}";
        assertThat(service.validate(request("ELM-1", "1")).resultCode()).isEqualTo(C2BResultCodes.INVALID_KYC);
        answer = "{\"valid\":false,\"result_code\":\"BOGUS\"}";
        assertThat(service.validate(request("ELM-1", "1")).resultCode()).isEqualTo(C2BResultCodes.INVALID_ACCOUNT);
    }

    @Test
    void aSlowLookupTimesOutAndFollowsThePolicy() {
        delayMs = 1500;
        C2BValidationRule reject = rule(null, null, null, url());
        reject.setLookupTimeoutMs(200);
        when(rules.findByShortcodeUuid(shortcode.getUuid())).thenReturn(Optional.of(reject));

        long started = System.nanoTime();
        ValidationDecision rejected = service.validate(request("ELM-004512", "4500"));
        long tookMs = (System.nanoTime() - started) / 1_000_000;

        assertThat(rejected.resultCode()).isEqualTo(C2BResultCodes.OTHER_ERROR);
        assertThat(rejected.reason()).contains("timed out");
        assertThat(tookMs).isLessThan(1200);

        reject.setOnLookupFailure(LookupFailurePolicy.ACCEPT);
        assertThat(service.validate(request("ELM-004512", "4500")).accepted()).isTrue();
    }

    @Test
    void aFailingOrUnreadableLookupFollowsThePolicy() {
        C2BValidationRule rule = rule(null, null, null, url());
        when(rules.findByShortcodeUuid(shortcode.getUuid())).thenReturn(Optional.of(rule));

        status = 500;
        assertThat(service.validate(request("ELM-004512", "4500")).resultCode()).isEqualTo(C2BResultCodes.OTHER_ERROR);
        status = 200;
        answer = "not json";
        assertThat(service.validate(request("ELM-004512", "4500")).accepted()).isFalse();
        rule.setOnLookupFailure(LookupFailurePolicy.ACCEPT);
        rule.setLookupUrl("http://127.0.0.1:1/unreachable");
        assertThat(service.validate(request("ELM-004512", "4500")).accepted()).isTrue();
    }

    @Test
    void patternErrorsExplainWhatIsWrong() {
        assertThat(C2BValidationService.patternError("^ELM-[0-9]{6}$")).isNull();
        assertThat(C2BValidationService.patternError("^(ELM")).startsWith("is not a valid regular expression");
    }

    private String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/lookup";
    }

    private C2BValidationRule rule(String pattern, String min, String max, String lookupUrl) {
        C2BValidationRule rule = new C2BValidationRule();
        rule.setShortcodeUuid(shortcode.getUuid());
        rule.setAccountPattern(pattern);
        rule.setMinAmount(min == null ? null : new BigDecimal(min));
        rule.setMaxAmount(max == null ? null : new BigDecimal(max));
        rule.setLookupUrl(lookupUrl);
        rule.setOnLookupFailure(LookupFailurePolicy.REJECT);
        return rule;
    }

    private static C2BValidationRequest request(String account, String amount) {
        return new C2BValidationRequest("Pay Bill", "TJQ4K2L9XA", "20261026143208", new BigDecimal(amount),
                "600100", account, null, null, "254712345678", "JANE", null, "KAMAU");
    }
}
