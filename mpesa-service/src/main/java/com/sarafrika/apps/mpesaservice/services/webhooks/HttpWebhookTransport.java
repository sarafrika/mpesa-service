package com.sarafrika.apps.mpesaservice.services.webhooks;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** java.net.http with short timeouts and no redirects, so a slow receiver never holds a worker for long. */
@Component
public class HttpWebhookTransport implements WebhookTransport {

    private final HttpClient client;
    private final Duration requestTimeout;

    public HttpWebhookTransport(@Value("${mpesa.webhooks.connect-timeout:3s}") Duration connectTimeout,
                                @Value("${mpesa.webhooks.request-timeout:5s}") Duration requestTimeout) {
        this.client = HttpClient.newBuilder().connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER).build();
        this.requestTimeout = requestTimeout;
    }

    @Override
    public int post(URI url, Map<String, String> headers, String body) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(url).timeout(requestTimeout)
                .POST(HttpRequest.BodyPublishers.ofString(body));
        headers.forEach(request::header);
        return client.send(request.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }
}
