package com.sarafrika.apps.mpesaservice.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

/** The console's Keycloak realm roles, read from {@code realm_access.roles} as {@code ROLE_<role>}. */
public final class KeycloakRealmRoles {

    public static final String VIEWER = "mpesa-viewer";
    public static final String OPERATOR = "mpesa-operator";
    public static final String FINANCE = "mpesa-finance";
    public static final String ADMIN = "mpesa-admin";

    private KeycloakRealmRoles() {
    }

    /** Admin implies finance and operator; both imply viewer. */
    public static RoleHierarchy hierarchy() {
        return RoleHierarchyImpl.withDefaultRolePrefix()
                .role(ADMIN).implies(FINANCE, OPERATOR)
                .role(FINANCE).implies(VIEWER)
                .role(OPERATOR).implies(VIEWER)
                .build();
    }

    /** Realm roles plus the usual SCOPE_ authorities. */
    public static JwtAuthenticationConverter authenticationConverter() {
        JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            List<GrantedAuthority> all = new ArrayList<>(scopes.convert(jwt));
            all.addAll(authorities(jwt));
            return all;
        });
        return converter;
    }

    static Collection<GrantedAuthority> authorities(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null || !(realmAccess.get("roles") instanceof Collection<?> roles)) {
            return List.of();
        }
        return roles.stream()
                .filter(String.class::isInstance)
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
    }
}
