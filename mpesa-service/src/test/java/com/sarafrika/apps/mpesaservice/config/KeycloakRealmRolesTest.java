package com.sarafrika.apps.mpesaservice.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.stereotype.Service;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/** Keycloak realm roles become ROLE_ authorities, and admin > finance/operator > viewer holds in @PreAuthorize. */
class KeycloakRealmRolesTest {

    @Test
    void theConverterReadsRealmRolesFromTheToken() {
        Jwt jwt = new Jwt("token", Instant.now(), Instant.now().plusSeconds(60), Map.of("alg", "none"),
                Map.of("sub", "u-1", "scope", "openid",
                        "realm_access", Map.of("roles", List.of("mpesa-finance", "offline_access"))));

        List<String> authorities = KeycloakRealmRoles.authenticationConverter().convert(jwt).getAuthorities()
                .stream().map(GrantedAuthority::getAuthority).toList();

        assertThat(authorities).contains("ROLE_mpesa-finance", "ROLE_offline_access", "SCOPE_openid");
    }

    @Test
    void aTokenWithoutRealmRolesHasNoRoles() {
        Jwt jwt = new Jwt("token", Instant.now(), Instant.now().plusSeconds(60), Map.of("alg", "none"),
                Map.of("sub", "service-account"));

        assertThat(KeycloakRealmRoles.authorities(jwt)).isEmpty();
    }

    @Nested
    @SpringJUnitWebConfig({CallbackSecurityConfig.class, KeycloakRealmRolesTest.Mvc.class, KeycloakRealmRolesTest.Guarded.class})
    class ThroughPreAuthorize {

        @Autowired
        private Guarded guarded;

        @Test
        @WithMockUser(roles = "mpesa-admin")
        void anAdminHoldsEveryRole() {
            assertThat(guarded.view()).isTrue();
            assertThat(guarded.operate()).isTrue();
            assertThat(guarded.finance()).isTrue();
            assertThat(guarded.admin()).isTrue();
        }

        @Test
        @WithMockUser(roles = "mpesa-finance")
        void financeImpliesViewerButNotOperator() {
            assertThat(guarded.view()).isTrue();
            assertThat(guarded.finance()).isTrue();
            assertThatThrownBy(guarded::operate).isInstanceOf(AuthorizationDeniedException.class);
            assertThatThrownBy(guarded::admin).isInstanceOf(AuthorizationDeniedException.class);
        }

        @Test
        @WithMockUser(roles = "mpesa-operator")
        void operatorImpliesViewerButNotFinance() {
            assertThat(guarded.view()).isTrue();
            assertThat(guarded.operate()).isTrue();
            assertThatThrownBy(guarded::finance).isInstanceOf(AuthorizationDeniedException.class);
        }

        @Test
        @WithMockUser(roles = "mpesa-viewer")
        void aViewerOnlyViews() {
            assertThat(guarded.view()).isTrue();
            assertThatThrownBy(guarded::operate).isInstanceOf(AuthorizationDeniedException.class);
            assertThatThrownBy(guarded::finance).isInstanceOf(AuthorizationDeniedException.class);
        }
    }

    @Configuration
    @EnableWebMvc
    static class Mvc {
    }

    @Service
    static class Guarded {

        @PreAuthorize("hasRole('mpesa-viewer')")
        public boolean view() {
            return true;
        }

        @PreAuthorize("hasRole('mpesa-operator')")
        public boolean operate() {
            return true;
        }

        @PreAuthorize("hasRole('mpesa-finance')")
        public boolean finance() {
            return true;
        }

        @PreAuthorize("hasRole('mpesa-admin')")
        public boolean admin() {
            return true;
        }
    }
}
