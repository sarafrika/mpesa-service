package com.sarafrika.apps.mpesaservice.services.webhooks;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** java.net.http with short timeouts, no redirects and one overall deadline (connect + request timeout)
 * that also covers a body trickled in slowly, so a slow receiver never holds a worker for long. */
@Component
public class HttpWebhookTransport implements WebhookTransport {

    private final HttpClient client;
    private final Duration requestTimeout;
    private final Duration deadline;

    public HttpWebhookTransport(@Value("${mpesa.webhooks.connect-timeout:3s}") Duration connectTimeout,
                                @Value("${mpesa.webhooks.request-timeout:5s}") Duration requestTimeout) {
        this.client = HttpClient.newBuilder().connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER).build();
        this.requestTimeout = requestTimeout;
        this.deadline = connectTimeout.plus(requestTimeout);
    }

    @Override
    public int post(URI url, Map<String, String> headers, String body) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(url).timeout(requestTimeout)
                .POST(HttpRequest.BodyPublishers.ofString(body));
        headers.forEach(request::header);
        CompletableFuture<HttpResponse<Void>> response =
                client.sendAsync(request.build(), HttpResponse.BodyHandlers.discarding());
        try {
            return response.get(deadline.toMillis(), TimeUnit.MILLISECONDS).statusCode();
        } catch (TimeoutException e) {
            response.cancel(true);
            throw new HttpTimeoutException("No complete answer within " + deadline.toMillis() + " ms");
        } catch (ExecutionException e) {
            if (e.getCause() instanceof IOException io) {
                throw io;
            }
            throw new IOException(e.getCause());
        } catch (InterruptedException e) {
            response.cancel(true);
            throw e;
        }
    }
}
