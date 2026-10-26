package com.sarafrika.apps.mpesaservice.services.products;

import com.sarafrika.apps.mpesaservice.exceptions.ProductAccessDeniedException;
import com.sarafrika.apps.mpesaservice.models.ClientShortcodeGrant;
import com.sarafrika.apps.mpesaservice.repositories.ClientShortcodeGrantRepository;
import com.sarafrika.apps.mpesaservice.utils.enums.GrantOperation;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Limits each product to the shortcodes and operations granted to its Keycloak client. Money-moving calls
 * always need a grant; console users (any mpesa-* realm role) may read without one.
 * mpesa.grants.enforce=false turns every check off. */
@Component
public class ProductGrantGuard {

    private final ClientShortcodeGrantRepository grants;
    private final boolean enforce;

    public ProductGrantGuard(ClientShortcodeGrantRepository grants,
                             @Value("${mpesa.grants.enforce:true}") boolean enforce) {
        this.grants = grants;
        this.enforce = enforce;
    }

    public boolean isEnforced() {
        return enforce;
    }

    /** For calls that move money (STK push, B2C): the caller's client must hold the operation on the shortcode. */
    public void require(Authentication authentication, UUID shortcodeUuid, GrantOperation operation) {
        if (!enforce || shortcodeUuid == null) {
            return;
        }
        Authentication caller = caller(authentication);
        String clientId = CallerIdentity.clientId(caller);
        boolean granted = grants.findByClientIdAndShortcodeUuid(clientId, shortcodeUuid)
                .map(grant -> grant.allows(operation)).orElse(false);
        if (!granted) {
            throw new ProductAccessDeniedException("Client '" + clientId + "' is not granted " + operation
                    + " on shortcode " + shortcodeUuid + "; an M-Pesa admin can grant it under Products in the console");
        }
    }

    /** For read endpoints before anything is loaded: console users, or a product with any of these grants. */
    public void requireReadAccess(Authentication authentication, GrantOperation... anyOf) {
        if (!enforce) {
            return;
        }
        Authentication caller = caller(authentication);
        if (CallerIdentity.isConsoleUser(caller)) {
            return;
        }
        String clientId = CallerIdentity.clientId(caller);
        boolean any = grants.findByClientIdAndActiveTrue(clientId).stream()
                .anyMatch(grant -> Arrays.stream(anyOf).anyMatch(grant::allows));
        if (!any) {
            throw new ProductAccessDeniedException("Client '" + clientId + "' has no " + names(anyOf)
                    + " grant on any shortcode; an M-Pesa admin can grant it under Products in the console");
        }
    }

    /** For a read about one shortcode: console users, or a product holding any of these operations on it. */
    public void requireRead(Authentication authentication, UUID shortcodeUuid, GrantOperation... anyOf) {
        if (!canRead(authentication, shortcodeUuid, anyOf)) {
            String clientId = CallerIdentity.clientId(caller(authentication));
            throw new ProductAccessDeniedException("Client '" + clientId + "' is not granted " + names(anyOf)
                    + " on shortcode " + shortcodeUuid);
        }
    }

    public boolean canRead(Authentication authentication, UUID shortcodeUuid, GrantOperation... anyOf) {
        if (!enforce) {
            return true;
        }
        Authentication caller = caller(authentication);
        if (CallerIdentity.isConsoleUser(caller)) {
            return true;
        }
        return readableShortcodes(CallerIdentity.clientId(caller), anyOf).contains(shortcodeUuid);
    }

    /** Keeps only the rows on shortcodes the caller may read. */
    public <T> List<T> visible(Authentication authentication, List<T> rows, Function<T, UUID> shortcodeOf,
                               GrantOperation... anyOf) {
        if (!enforce) {
            return rows;
        }
        Authentication caller = caller(authentication);
        if (CallerIdentity.isConsoleUser(caller)) {
            return rows;
        }
        Set<UUID> readable = readableShortcodes(CallerIdentity.clientId(caller), anyOf);
        return rows.stream().filter(row -> readable.contains(shortcodeOf.apply(row))).toList();
    }

    private Set<UUID> readableShortcodes(String clientId, GrantOperation... anyOf) {
        return grants.findByClientIdAndActiveTrue(clientId).stream()
                .filter(grant -> Arrays.stream(anyOf).anyMatch(grant::allows))
                .map(ClientShortcodeGrant::getShortcodeUuid)
                .collect(Collectors.toSet());
    }

    private static Authentication caller(Authentication authentication) {
        return authentication != null ? authentication : SecurityContextHolder.getContext().getAuthentication();
    }

    private static String names(GrantOperation... operations) {
        return Arrays.stream(operations).map(Enum::name).collect(Collectors.joining(" or "));
    }
}
