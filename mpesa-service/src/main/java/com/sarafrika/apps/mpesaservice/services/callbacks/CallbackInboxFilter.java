package com.sarafrika.apps.mpesaservice.services.callbacks;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sarafrika.apps.mpesaservice.config.ClientIpResolver;
import com.sarafrika.apps.mpesaservice.services.callbacks.CallbackAcks.Processing;
import com.sarafrika.apps.mpesaservice.utils.enums.CallbackKind;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/** Records every request to /api/v1/callbacks/** in the inbox before its handler runs, then marks it
 * PROCESSED or FAILED from the ack (and any CallbackOutcome report). Runs after Spring Security, so
 * requests the IP allow-list refused are not stored. */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 100)
public class CallbackInboxFilter extends OncePerRequestFilter {

    static final List<String> KEPT_HEADERS = List.of("Content-Type", "Content-Length", "User-Agent",
            "X-Forwarded-For", "X-Forwarded-Proto", "X-Request-Id");

    private final ObjectProvider<CallbackInboxRecorder> recorder;
    private final ObjectProvider<ClientIpResolver> clientIpResolver;
    private final ObjectMapper objectMapper;

    public CallbackInboxFilter(ObjectProvider<CallbackInboxRecorder> recorder,
                               ObjectProvider<ClientIpResolver> clientIpResolver, ObjectMapper objectMapper) {
        this.recorder = recorder;
        this.clientIpResolver = clientIpResolver;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(CallbackKind.PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        CallbackInboxRecorder inbox = recorder.getIfAvailable();
        if (inbox == null) {
            chain.doFilter(request, response);
            return;
        }
        String endpoint = request.getRequestURI();
        byte[] body = request.getInputStream().readAllBytes();
        UUID entry = record(() -> inbox.received(endpoint, sourceIp(request), headers(request), body));

        ContentCachingResponseWrapper cached = new ContentCachingResponseWrapper(response);
        Throwable thrown = null;
        CallbackOutcome.begin();
        try {
            chain.doFilter(new CachedBodyRequest(request, body), cached);
        } catch (IOException | ServletException | RuntimeException e) {
            thrown = e;
            throw e;
        } finally {
            String reported = CallbackOutcome.end();
            int status = cached.getStatus();
            byte[] ackBody = cached.getContentAsByteArray();
            cached.copyBodyToResponse();
            if (entry != null) {
                Throwable failure = thrown;
                record(() -> {
                    Processing processing = failure != null ? Processing.failure(CallbackOutcome.describe(failure))
                            : CallbackAcks.read(CallbackKind.fromPath(endpoint), status, ack(ackBody), reported);
                    inbox.finished(entry, processing);
                    return null;
                });
            }
        }
    }

    // The recorder already catches its own failures; this is the last guard so the ack always goes out.
    private static UUID record(java.util.function.Supplier<UUID> step) {
        try {
            return step.get();
        } catch (RuntimeException e) {
            LoggerFactory.getLogger(CallbackInboxFilter.class).error("Callback inbox failed: {}", e.toString());
            return null;
        }
    }

    private JsonNode ack(byte[] content) {
        try {
            return content.length == 0 ? null : objectMapper.readTree(content);
        } catch (Exception notJson) {
            return null;
        }
    }

    private String sourceIp(HttpServletRequest request) {
        ClientIpResolver resolver = clientIpResolver.getIfAvailable();
        return resolver == null ? request.getRemoteAddr() : resolver.resolve(request);
    }

    static Map<String, String> headers(HttpServletRequest request) {
        Map<String, String> kept = new LinkedHashMap<>();
        for (String name : KEPT_HEADERS) {
            String value = request.getHeader(name);
            if (value != null) {
                kept.put(name, value.length() > 300 ? value.substring(0, 300) : value);
            }
        }
        return kept;
    }

    /** Hands the handler the bytes the filter already read. */
    static final class CachedBodyRequest extends HttpServletRequestWrapper {

        private final byte[] body;

        CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream in = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return in.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException("Synchronous reads only");
                }

                @Override
                public int read() {
                    return in.read();
                }

                @Override
                public int read(byte[] b, int off, int len) {
                    return in.read(b, off, len);
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            String encoding = getCharacterEncoding();
            return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(body),
                    encoding == null ? StandardCharsets.UTF_8 : java.nio.charset.Charset.forName(encoding)));
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }
    }
}
