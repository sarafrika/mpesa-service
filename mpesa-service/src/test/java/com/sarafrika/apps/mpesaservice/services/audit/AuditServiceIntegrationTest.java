package com.sarafrika.apps.mpesaservice.services.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sarafrika.apps.mpesaservice.config.ClientIpResolver;
import com.sarafrika.apps.mpesaservice.dtos.admin.AuditEventDto;
import com.sarafrika.apps.mpesaservice.models.AuditEvent;
import com.sarafrika.apps.mpesaservice.repositories.AuditEventRepository;
import com.sarafrika.apps.mpesaservice.services.security.SecretCipher;
import com.sarafrika.apps.mpesaservice.utils.PageRequests;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Audit events land in the real audit_events table, redacted, attributed, filtered and append-only. */
@DataJpaTest(properties = {
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migrations",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect"})
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({AuditService.class, SecretCipher.class, AuditServiceIntegrationTest.Beans.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AuditServiceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private AuditService auditService;
    @Autowired
    private AuditEventRepository repository;
    @Autowired
    private JdbcTemplate jdbc;

    @TestConfiguration
    static class Beans {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }

        @Bean
        ClientIpResolver clientIpResolver() {
            return new ClientIpResolver(List.of("10.0.0.1"));
        }
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void recordsTheActorTheCallerIpAndRedactedSnapshots() {
        signIn(Map.of("sub", "u-1", "preferred_username", "jane.doe", "name", "Jane Doe"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "41.90.1.2");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        AuditEvent saved = auditService.record("SHORTCODE_UPDATED", "SHORTCODE", "sc-1",
                Map.of("business_name", "Old", "consumer_secret", "s3cr3t"),
                Map.of("business_name", "New", "credentials", Map.of("initiatorPassword", "pw", "api_token", "t"),
                        "items", List.of(Map.of("passkey", "pk"))));

        AuditEventDto stored = auditService.list(
                new AuditService.AuditQuery(null, null, null, "sc-1"), newestFirst(0, 25))
                .getContent().getFirst();
        assertThat(stored.uuid()).isEqualTo(saved.getUuid());
        assertThat(stored.actor()).isEqualTo("jane.doe");
        assertThat(stored.actorName()).isEqualTo("Jane Doe");
        assertThat(stored.ip()).isEqualTo("41.90.1.2");
        assertThat(stored.createdAt()).isNotNull();
        assertThat(stored.before().path("business_name").asText()).isEqualTo("Old");
        assertThat(stored.before().path("consumer_secret").asText()).isEqualTo(AuditService.REDACTED);
        assertThat(stored.after().path("credentials").path("initiatorPassword").asText()).isEqualTo(AuditService.REDACTED);
        assertThat(stored.after().path("credentials").path("api_token").asText()).isEqualTo(AuditService.REDACTED);
        assertThat(stored.after().path("items").path(0).path("passkey").asText()).isEqualTo(AuditService.REDACTED);
        assertThat(stored.after().toString()).doesNotContain("s3cr3t", "\"pw\"", "\"pk\"");
    }

    @Test
    void fallsBackToTheSubjectThenToSystem() {
        signIn(Map.of("sub", "service-account-elimika"));
        auditService.record("PROBE", "FALLBACK", "subject", null, Map.of("a", 1));
        SecurityContextHolder.clearContext();
        auditService.record("PROBE", "FALLBACK", "system", Map.of("a", 1), null);

        List<AuditEventDto> events = auditService.list(
                new AuditService.AuditQuery(null, "PROBE", "FALLBACK", null), newestFirst(0, 25)).getContent();
        assertThat(events).extracting(AuditEventDto::actor).containsExactly("system", "service-account-elimika");
        assertThat(events.getFirst().ip()).isNull();
        assertThat(events.getFirst().after()).isNull();
    }

    @Test
    void listsNewestFirstWithFiltersAndPaging() {
        for (int i = 0; i < 3; i++) {
            auditService.record("PAGED", "PAGING", "p-" + i, null, Map.of("n", i));
        }
        auditService.record("OTHER", "PAGING", "p-x", null, null);

        Page<AuditEventDto> first = auditService.list(new AuditService.AuditQuery("system", "PAGED", "PAGING", null),
                newestFirst(0, 2));
        Page<AuditEventDto> second = auditService.list(new AuditService.AuditQuery(null, "PAGED", null, null),
                newestFirst(1, 2));

        assertThat(first.getTotalElements()).isEqualTo(3);
        assertThat(first.getContent()).extracting(AuditEventDto::entityId).containsExactly("p-2", "p-1");
        assertThat(second.getContent()).extracting(AuditEventDto::entityId).containsExactly("p-0");
        assertThat(first.getContent().getFirst().createdAt()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    void theTableRefusesUpdatesAndDeletes() {
        AuditEvent saved = auditService.record("IMMUTABLE", "AUDIT", "a-1", null, null);

        assertThatThrownBy(() -> jdbc.update("UPDATE audit_events SET action = 'X' WHERE id = ?", saved.getId()))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM audit_events WHERE id = ?", saved.getId()))
                .hasMessageContaining("append-only");
        assertThat(repository.findById(saved.getId())).isPresent();
    }

    private static PageRequest newestFirst(int page, int size) {
        return PageRequests.of(page, size, null, Map.of(), Sort.by(Sort.Order.desc("createdAt")));
    }

    private static void signIn(Map<String, Object> claims) {
        Jwt jwt = new Jwt("token", Instant.now(), Instant.now().plusSeconds(60), Map.of("alg", "none"), claims);
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
}
