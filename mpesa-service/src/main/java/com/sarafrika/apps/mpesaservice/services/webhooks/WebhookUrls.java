package com.sarafrika.apps.mpesaservice.services.webhooks;

import com.sarafrika.apps.mpesaservice.exceptions.InvalidRequestException;
import com.sarafrika.apps.mpesaservice.services.security.OutboundUrlGuard;

/** Webhook URLs must be absolute https to a public address (see {@link OutboundUrlGuard}); plain
 * http://localhost only when mpesa.outbound.allow-localhost is on for development. */
public final class WebhookUrls {

    private WebhookUrls() {
    }

    public static String validated(String field, String url, OutboundUrlGuard guard) {
        if (url == null || url.isBlank()) {
            throw new InvalidRequestException(field, "is required");
        }
        try {
            return guard.check(url).toString();
        } catch (OutboundUrlGuard.BlockedUrlException e) {
            throw new InvalidRequestException(field, e.getMessage());
        }
    }
}
