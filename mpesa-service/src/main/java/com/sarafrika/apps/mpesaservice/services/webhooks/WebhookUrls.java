package com.sarafrika.apps.mpesaservice.services.webhooks;

import com.sarafrika.apps.mpesaservice.exceptions.InvalidRequestException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;

/** Webhook URLs must be absolute https; plain http is allowed only for localhost while developing. */
public final class WebhookUrls {

    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "[::1]", "::1");

    private WebhookUrls() {
    }

    public static String validated(String field, String url) {
        if (url == null || url.isBlank()) {
            throw new InvalidRequestException(field, "is required");
        }
        URI uri;
        try {
            uri = new URI(url.trim());
        } catch (URISyntaxException e) {
            throw new InvalidRequestException(field, "is not a valid URL");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (host.isEmpty()) {
            throw new InvalidRequestException(field, "must be an absolute URL with a host");
        }
        if (uri.getUserInfo() != null) {
            throw new InvalidRequestException(field, "must not contain credentials");
        }
        boolean local = LOCAL_HOSTS.contains(host);
        if (!scheme.equals("https") && !(scheme.equals("http") && local)) {
            throw new InvalidRequestException(field, "must use https (http is allowed only for localhost)");
        }
        return uri.toString();
    }
}
