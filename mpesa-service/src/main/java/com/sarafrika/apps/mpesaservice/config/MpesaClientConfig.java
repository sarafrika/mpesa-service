package com.sarafrika.apps.mpesaservice.config;

import com.sarafrika.apps.mpesaservice.clients.MpesaDarajaHttpClient;
import com.sarafrika.apps.mpesaservice.utils.enums.Environment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.time.Duration;

/**
 * Configuration for M-Pesa Daraja API RestClient and HTTP Interface
 */
@Configuration
@Slf4j
public class MpesaClientConfig {

    @Value("${mpesa.daraja.sandbox.base-url:https://sandbox.safaricom.co.ke}")
    private String sandboxBaseUrl;

    @Value("${mpesa.daraja.production.base-url:https://api.safaricom.co.ke}")
    private String productionBaseUrl;

    @Value("${mpesa.daraja.connect-timeout:30s}")
    private Duration connectTimeout;

    @Value("${mpesa.daraja.read-timeout:60s}")
    private Duration readTimeout;

    /**
     * RestClient for Sandbox environment
     */
    @Bean("sandboxRestClient")
    public RestClient sandboxRestClient() {
        return RestClient.builder()
                .baseUrl(sandboxBaseUrl)
                .requestInterceptor((request, body, execution) -> {
                    log.debug("Sandbox API Request: {} {}", request.getMethod(), request.getURI());
                    return execution.execute(request, body);
                })
                .defaultStatusHandler(HttpStatusCode::is4xxClientError, (request, response) -> {
                    log.error("Sandbox Client Error: {} - {}", response.getStatusCode(),
                            new String(response.getBody().readAllBytes()));
                    throw new RuntimeException("Client error: " + response.getStatusCode());
                })
                .defaultStatusHandler(HttpStatusCode::is5xxServerError, (request, response) -> {
                    log.error("Sandbox Server Error: {} - {}", response.getStatusCode(),
                            new String(response.getBody().readAllBytes()));
                    throw new RuntimeException("Server error: " + response.getStatusCode());
                })
                .build();
    }

    /**
     * RestClient for Production environment
     */
    @Bean("productionRestClient")
    public RestClient productionRestClient() {
        return RestClient.builder()
                .baseUrl(productionBaseUrl)
                .requestInterceptor((request, body, execution) -> {
                    log.debug("Production API Request: {} {}", request.getMethod(), request.getURI());
                    return execution.execute(request, body);
                })
                .defaultStatusHandler(HttpStatusCode::is4xxClientError, (request, response) -> {
                    log.error("Production Client Error: {} - {}", response.getStatusCode(),
                            new String(response.getBody().readAllBytes()));
                    throw new RuntimeException("Client error: " + response.getStatusCode());
                })
                .defaultStatusHandler(HttpStatusCode::is5xxServerError, (request, response) -> {
                    log.error("Production Server Error: {} - {}", response.getStatusCode(),
                            new String(response.getBody().readAllBytes()));
                    throw new RuntimeException("Server error: " + response.getStatusCode());
                })
                .build();
    }

    /**
     * HTTP Interface client for Sandbox environment
     */
    @Bean("sandboxHttpClient")
    public MpesaDarajaHttpClient sandboxHttpClient() {
        RestClient restClient = sandboxRestClient();
        RestClientAdapter adapter = RestClientAdapter.create(restClient);
        HttpServiceProxyFactory factory = HttpServiceProxyFactory.builderFor(adapter).build();
        return factory.createClient(MpesaDarajaHttpClient.class);
    }

    /**
     * HTTP Interface client for Production environment
     */
    @Bean("productionHttpClient")
    public MpesaDarajaHttpClient productionHttpClient() {
        RestClient restClient = productionRestClient();
        RestClientAdapter adapter = RestClientAdapter.create(restClient);
        HttpServiceProxyFactory factory = HttpServiceProxyFactory.builderFor(adapter).build();
        return factory.createClient(MpesaDarajaHttpClient.class);
    }

    /**
     * Factory to get the appropriate HTTP client based on environment
     */
    @Bean
    public MpesaHttpClientFactory mpesaHttpClientFactory() {
        return new MpesaHttpClientFactory();
    }

    /**
     * Factory class to provide environment-specific HTTP clients
     */
    public static class MpesaHttpClientFactory {

        private MpesaDarajaHttpClient sandboxClient;
        private MpesaDarajaHttpClient productionClient;

        public void setSandboxClient(MpesaDarajaHttpClient sandboxClient) {
            this.sandboxClient = sandboxClient;
        }

        public void setProductionClient(MpesaDarajaHttpClient productionClient) {
            this.productionClient = productionClient;
        }

        /**
         * Get HTTP client based on environment
         */
        public MpesaDarajaHttpClient getClient(Environment environment) {
            return environment == Environment.PRODUCTION ? productionClient : sandboxClient;
        }
    }
}