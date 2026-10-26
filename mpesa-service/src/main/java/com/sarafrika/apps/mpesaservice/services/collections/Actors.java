package com.sarafrika.apps.mpesaservice.services.collections;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.StringUtils;

/** Who is acting, named the way the audit trail names them; "system" for scheduled jobs. */
final class Actors {

    static final String SYSTEM = "system";

    private Actors() {
    }

    static String current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            return SYSTEM;
        }
        if (authentication instanceof JwtAuthenticationToken jwt) {
            String username = jwt.getToken().getClaimAsString("preferred_username");
            if (StringUtils.hasText(username)) {
                return username;
            }
        }
        return StringUtils.hasText(authentication.getName()) ? authentication.getName() : SYSTEM;
    }
}
