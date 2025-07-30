package com.sarafrika.apps.mpesaservice.config;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

/**
 * Security configuration for M-Pesa callback endpoints
 * <p>
 * This configuration:
 * <ol>
 * <li>Allows unauthenticated access to callback endpoints (M-Pesa can't authenticate)</li>
 * <li>Implements IP whitelisting for additional security</li>
 * <li>Adds request logging for audit purposes</li>
 * <li>Protects other endpoints with standard authentication</li>
 * </ol>
 * </p>
 */
@Configuration
@EnableWebSecurity
@Slf4j
public class CallbackSecurityConfig {

    @Value("${mpesa.callback.allowed-ips:196.201.214.200,196.201.214.206,196.201.213.114,196.201.214.207,196.201.214.208,196.201.213.44,196.201.212.127,196.201.212.138}")
    private List<String> allowedIps;

    @Value("${mpesa.callback.ip-whitelist.enabled:true}")
    private boolean ipWhitelistEnabled;

    @Bean
    public SecurityFilterChain callbackSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/callbacks/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().authenticated()
                );

        if (ipWhitelistEnabled) {
            http.addFilterBefore(new CallbackIPWhitelistFilter(allowedIps), UsernamePasswordAuthenticationFilter.class);
        }

        http.addFilterAfter(new CallbackLoggingFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * IP Whitelist Filter for M-Pesa callback endpoints
     * Only allows requests from Safaricom's known IP addresses
     */
    public static class CallbackIPWhitelistFilter extends OncePerRequestFilter {

        private final List<String> allowedIps;

        public CallbackIPWhitelistFilter(List<String> allowedIps) {
            this.allowedIps = allowedIps;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                        FilterChain filterChain) throws ServletException, IOException {

            String requestPath = request.getRequestURI();

            // Only apply IP filtering to callback endpoints
            if (requestPath.startsWith("/api/v1/callbacks/")) {
                String clientIp = getClientIpAddress(request);

                log.debug("Callback request from IP: {} to path: {}", clientIp, requestPath);

                if (!isIpAllowed(clientIp)) {
                    log.warn("Rejected callback request from unauthorized IP: {} to path: {}", clientIp, requestPath);
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.getWriter().write("{\"error\":\"Access denied from this IP address\"}");
                    response.setContentType("application/json");
                    return;
                }

                log.info("Accepted callback request from authorized IP: {} to path: {}", clientIp, requestPath);
            }

            filterChain.doFilter(request, response);
        }

        private String getClientIpAddress(HttpServletRequest request) {
            // Check for IP in various headers (useful when behind load balancers/proxies)
            String[] headerNames = {
                    "X-Forwarded-For",
                    "X-Real-IP",
                    "Proxy-Client-IP",
                    "WL-Proxy-Client-IP",
                    "HTTP_X_FORWARDED_FOR",
                    "HTTP_X_FORWARDED",
                    "HTTP_X_CLUSTER_CLIENT_IP",
                    "HTTP_CLIENT_IP",
                    "HTTP_FORWARDED_FOR",
                    "HTTP_FORWARDED",
                    "HTTP_VIA",
                    "REMOTE_ADDR"
            };

            for (String header : headerNames) {
                String ip = request.getHeader(header);
                if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                    // Handle comma-separated IPs (X-Forwarded-For can contain multiple IPs)
                    if (ip.contains(",")) {
                        ip = ip.split(",")[0].trim();
                    }
                    return ip;
                }
            }

            return request.getRemoteAddr();
        }

        private boolean isIpAllowed(String clientIp) {
            if (allowedIps == null || allowedIps.isEmpty()) {
                return true; // If no whitelist configured, allow all
            }

            return allowedIps.contains(clientIp);
        }
    }

    /**
     * Request Logging Filter for callback endpoints
     * Logs all callback requests for audit and debugging purposes
     */
    public static class CallbackLoggingFilter extends OncePerRequestFilter {

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                        FilterChain filterChain) throws ServletException, IOException {

            String requestPath = request.getRequestURI();

            // Only log callback endpoints
            if (requestPath.startsWith("/api/v1/callbacks/")) {
                long startTime = System.currentTimeMillis();
                String clientIp = request.getRemoteAddr();
                String method = request.getMethod();
                String userAgent = request.getHeader("User-Agent");

                log.info("CALLBACK REQUEST - Method: {}, Path: {}, IP: {}, User-Agent: {}",
                        method, requestPath, clientIp, userAgent);

                try {
                    filterChain.doFilter(request, response);

                    long processingTime = System.currentTimeMillis() - startTime;
                    int statusCode = response.getStatus();

                    log.info("CALLBACK RESPONSE - Path: {}, Status: {}, Processing Time: {}ms",
                            requestPath, statusCode, processingTime);

                } catch (Exception e) {
                    long processingTime = System.currentTimeMillis() - startTime;
                    log.error("CALLBACK ERROR - Path: {}, Processing Time: {}ms, Error: {}",
                            requestPath, processingTime, e.getMessage(), e);
                    throw e;
                }
            } else {
                filterChain.doFilter(request, response);
            }
        }
    }
}