package com.sarafrika.apps.mpesaservice.services.shortcode;

import com.sarafrika.apps.mpesaservice.config.KeycloakRealmRoles;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.StringUtils;

/** Who is calling, named the way the audit trail names them (preferred_username, then subject). */
final class CurrentActor {

    private static final String SYSTEM = "system";

    private CurrentActor() {
    }

    static String name() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            return SYSTEM;
        }
        if (authentication instanceof JwtAuthenticationToken jwt) {
            String username = jwt.getToken().getClaimAsString("preferred_username");
            if (StringUtils.hasText(username)) {
                return username;
            }
            if (StringUtils.hasText(jwt.getToken().getSubject())) {
                return jwt.getToken().getSubject();
            }
        }
        return StringUtils.hasText(authentication.getName()) ? authentication.getName() : SYSTEM;
    }

    /** mpesa-admin sits at the top of the hierarchy, so only the role itself grants it. */
    static boolean isAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> ("ROLE_" + KeycloakRealmRoles.ADMIN).equals(a.getAuthority()));
    }
}
