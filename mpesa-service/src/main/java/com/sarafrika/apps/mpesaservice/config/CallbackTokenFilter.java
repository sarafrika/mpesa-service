package com.sarafrika.apps.mpesaservice.config;

import com.sarafrika.apps.mpesaservice.models.MpesaShortCode;
import com.sarafrika.apps.mpesaservice.repositories.MpesaShortCodeRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Accepts /api/v1/callbacks/{callback token}/... for a shortcode's own token and hands the request on as the
 * shared path (/api/v1/callbacks/...), so the token never reaches handlers, logs or the inbox. A segment long
 * enough to be a token that matches no shortcode is refused with 403; the shared paths pass untouched.
 */
@Slf4j
public class CallbackTokenFilter extends OncePerRequestFilter {

    public static final String PREFIX = "/api/v1/callbacks/";
    /** Request attribute holding the uuid of the shortcode whose token the callback used. */
    public static final String SHORTCODE_UUID = CallbackTokenFilter.class.getName() + ".shortcodeUuid";
    // Route names under /api/v1/callbacks are short words; tokens are 64 hex characters.
    private static final int MIN_TOKEN_LENGTH = 32;

    private final ObjectProvider<MpesaShortCodeRepository> shortcodes;

    public CallbackTokenFilter(ObjectProvider<MpesaShortCodeRepository> shortcodes) {
        this.shortcodes = shortcodes;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String rest = request.getRequestURI().substring(PREFIX.length());
        int slash = rest.indexOf('/');
        String first = slash < 0 ? rest : rest.substring(0, slash);
        if (first.length() < MIN_TOKEN_LENGTH) {
            chain.doFilter(request, response);
            return;
        }
        Optional<MpesaShortCode> owner = slash < 0 ? Optional.empty() : lookup(first);
        if (owner.isEmpty()) {
            log.warn("Refused a callback to an unknown callback token from {}", request.getRemoteAddr());
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Unknown callback URL\"}");
            return;
        }
        UUID uuid = owner.get().getUuid();
        request.setAttribute(SHORTCODE_UUID, uuid);
        chain.doFilter(new SharedPathRequest(request, PREFIX + rest.substring(slash + 1)), response);
    }

    private Optional<MpesaShortCode> lookup(String token) {
        MpesaShortCodeRepository repository = shortcodes.getIfAvailable();
        if (repository == null || !token.matches("[0-9a-f]+")) {
            return Optional.empty();
        }
        return repository.findByCallbackToken(token);
    }

    /** The request as if it had been sent to the shared path. */
    static final class SharedPathRequest extends HttpServletRequestWrapper {

        private final String uri;

        SharedPathRequest(HttpServletRequest request, String uri) {
            super(request);
            this.uri = uri;
        }

        @Override
        public String getRequestURI() {
            return getContextPath() + uri;
        }

        @Override
        public StringBuffer getRequestURL() {
            StringBuffer url = new StringBuffer();
            url.append(getScheme()).append("://").append(getServerName()).append(':').append(getServerPort())
                    .append(getRequestURI());
            return url;
        }

        @Override
        public String getServletPath() {
            return uri;
        }

        @Override
        public String getPathInfo() {
            return null;
        }
    }
}
