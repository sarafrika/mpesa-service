package com.sarafrika.apps.mpesaservice.controllers;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import com.sarafrika.apps.mpesaservice.models.ClientShortcodeGrant;
import com.sarafrika.apps.mpesaservice.utils.enums.GrantOperation;
import java.util.EnumSet;
import java.util.UUID;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Tokens as Keycloak issues them: products carry only azp, console users also carry a realm role. */
final class MachineApiTokens {

    private MachineApiTokens() {
    }

    static RequestPostProcessor product(String azp) {
        return jwt().jwt(token -> token.claim("azp", azp).subject("service-account-" + azp));
    }

    static RequestPostProcessor console(String role) {
        return jwt().jwt(token -> token.claim("azp", "mpesa-console").claim("preferred_username", "jane.doe"))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    static ClientShortcodeGrant grant(String client, UUID shortcode, GrantOperation first, GrantOperation... rest) {
        ClientShortcodeGrant grant = new ClientShortcodeGrant();
        grant.setClientId(client);
        grant.setShortcodeUuid(shortcode);
        grant.setOperations(EnumSet.of(first, rest));
        return grant;
    }
}
