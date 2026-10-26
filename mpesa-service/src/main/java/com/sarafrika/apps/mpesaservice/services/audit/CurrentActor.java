package com.sarafrika.apps.mpesaservice.services.audit;

import com.sarafrika.apps.mpesaservice.config.KeycloakRealmRoles;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.StringUtils;

// Who is acting, for the audit trail and every created_by/updated_by column: preferred_username,
// then the token subject, then the principal name; "system" for scheduled jobs.
public final class CurrentActor {

    public static final String SYSTEM = "system";
    private static final int MAX_LENGTH = 100;

    private CurrentActor() {
    }

    public static String name() {
        return name(SecurityContextHolder.getContext().getAuthentication());
    }

    public static String name(Authentication authentication) {
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            return SYSTEM;
        }
        String name = authentication.getName();
        if (authentication instanceof JwtAuthenticationToken jwt) {
            String username = jwt.getToken().getClaimAsString("preferred_username");
            name = StringUtils.hasText(username) ? username
                    : StringUtils.hasText(jwt.getToken().getSubject()) ? jwt.getToken().getSubject() : name;
        }
        if (!StringUtils.hasText(name)) {
            return SYSTEM;
        }
        return name.length() > MAX_LENGTH ? name.substring(0, MAX_LENGTH) : name;
    }

    /** The signed-in person's JWT "sub" (stable in Keycloak, unlike the username), or null without one. */
    public static String subject() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwt) {
            String sub = jwt.getToken().getSubject();
            return StringUtils.hasText(sub) && sub.length() <= 255 ? sub : null;
        }
        return null;
    }

    /** mpesa-admin sits at the top of the hierarchy, so only the role itself grants it. */
    public static boolean isAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> ("ROLE_" + KeycloakRealmRoles.ADMIN).equals(a.getAuthority()));
    }
}
