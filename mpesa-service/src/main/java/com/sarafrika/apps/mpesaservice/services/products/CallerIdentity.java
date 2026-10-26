package com.sarafrika.apps.mpesaservice.services.products;

import com.sarafrika.apps.mpesaservice.config.KeycloakRealmRoles;
import java.util.Set;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Who is calling the machine API: the Keycloak client (azp) and whether it is a person using the console. */
public final class CallerIdentity {

    public static final String ANONYMOUS_CLIENT = "anonymous";
    private static final Set<String> CONSOLE_AUTHORITIES = Set.of(
            "ROLE_" + KeycloakRealmRoles.VIEWER, "ROLE_" + KeycloakRealmRoles.OPERATOR,
            "ROLE_" + KeycloakRealmRoles.FINANCE, "ROLE_" + KeycloakRealmRoles.ADMIN);

    private CallerIdentity() {
    }

    /** The calling Keycloak client (azp, else client_id), so one product never sees another's data. */
    public static String clientId(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwt) {
            String azp = jwt.getToken().getClaimAsString("azp");
            if (azp != null && !azp.isBlank()) {
                return azp;
            }
            String clientId = jwt.getToken().getClaimAsString("client_id");
            if (clientId != null && !clientId.isBlank()) {
                return clientId;
            }
        }
        return authentication == null || authentication.getName() == null ? ANONYMOUS_CLIENT : authentication.getName();
    }

    /** People signed in to the console carry a realm role; products (client credentials) do not. */
    public static boolean isConsoleUser(Authentication authentication) {
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            return false;
        }
        return authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                .anyMatch(CONSOLE_AUTHORITIES::contains);
    }

    /** The signed-in person for created_by/updated_by: preferred_username, else the principal name. */
    public static String actor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            return "system";
        }
        if (authentication instanceof JwtAuthenticationToken jwt) {
            String username = jwt.getToken().getClaimAsString("preferred_username");
            if (username != null && !username.isBlank()) {
                return username;
            }
        }
        String name = authentication.getName();
        return name == null || name.isBlank() ? "system" : name.length() > 100 ? name.substring(0, 100) : name;
    }
}
