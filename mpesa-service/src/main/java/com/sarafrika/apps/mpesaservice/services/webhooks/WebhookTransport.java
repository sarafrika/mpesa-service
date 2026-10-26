package com.sarafrika.apps.mpesaservice.services.webhooks;

import java.io.IOException;
import java.net.URI;
import java.util.Map;

/** Posts one signed webhook body; a seam so tests never open sockets. */
public interface WebhookTransport {

    /** Returns the HTTP status; throws on timeouts and connection failures. */
    int post(URI url, Map<String, String> headers, String body) throws IOException, InterruptedException;
}
