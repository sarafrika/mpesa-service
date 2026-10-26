package com.sarafrika.apps.mpesaservice.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** Callbacks are judged by the direct peer; X-Forwarded-For counts only when that peer is a trusted proxy. */
class CallbackIpWhitelistFilterTest {

    private static final String SAFARICOM = "196.201.214.200";
    private static final List<String> ALLOWED = List.of(SAFARICOM);

    @Test
    void withoutTrustedProxiesForwardingHeadersAreIgnored() throws Exception {
        MockHttpServletRequest request = callback("203.0.113.9");
        request.addHeader("X-Forwarded-For", SAFARICOM);
        request.addHeader("X-Real-IP", SAFARICOM);

        assertThat(filter(List.of(), request)).isEqualTo(403);
    }

    @Test
    void aSafaricomPeerIsAcceptedDirectly() throws Exception {
        assertThat(filter(List.of(), callback(SAFARICOM))).isEqualTo(200);
    }

    @Test
    void behindATrustedProxyTheRightMostUntrustedHopIsTheCaller() throws Exception {
        MockHttpServletRequest forged = callback("10.0.0.5");
        // The client wrote the first entry itself; the proxy appended the address it actually saw.
        forged.addHeader("X-Forwarded-For", SAFARICOM + ", 203.0.113.9");
        MockHttpServletRequest genuine = callback("10.0.0.5");
        genuine.addHeader("X-Forwarded-For", "198.51.100.1, " + SAFARICOM + ", 10.0.0.7");

        assertThat(filter(List.of("10.0.0.0/24"), forged)).isEqualTo(403);
        assertThat(filter(List.of("10.0.0.0/24"), genuine)).isEqualTo(200);
    }

    @Test
    void anUntrustedPeerCannotForwardForSafaricom() throws Exception {
        MockHttpServletRequest request = callback("203.0.113.9");
        request.addHeader("X-Forwarded-For", SAFARICOM);

        assertThat(filter(List.of("10.0.0.5"), request)).isEqualTo(403);
    }

    @Test
    void otherPathsAreNotFiltered() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/mpesa/payments");
        request.setRemoteAddr("203.0.113.9");

        assertThat(filter(List.of(), request)).isEqualTo(200);
    }

    private static MockHttpServletRequest callback(String peer) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/callbacks/stk/callback");
        request.setRemoteAddr(peer);
        return request;
    }

    private static int filter(List<String> trustedProxies, MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        new CallbackSecurityConfig.CallbackIPWhitelistFilter(ALLOWED, new ClientIpResolver(trustedProxies))
                .doFilter(request, response, new MockFilterChain());
        return response.getStatus();
    }
}
