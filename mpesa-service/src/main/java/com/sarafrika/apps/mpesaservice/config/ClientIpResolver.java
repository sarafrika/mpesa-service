package com.sarafrika.apps.mpesaservice.config;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.util.StringUtils;

/** The caller's address: the direct peer, unless that peer is a trusted proxy (IP or CIDR), in which case
 * the right-most X-Forwarded-For entry that is not itself a trusted proxy. */
public class ClientIpResolver {

    private static final Pattern IP_LITERAL = Pattern.compile("^(\\d{1,3}(\\.\\d{1,3}){3}|[0-9a-fA-F:]*:[0-9a-fA-F:.]*)$");

    private final List<IpAddressMatcher> trustedProxies;

    public ClientIpResolver(List<String> trustedProxies) {
        this.trustedProxies = trustedProxies == null ? List.of() : trustedProxies.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .map(IpAddressMatcher::new)
                .toList();
    }

    public String resolve(HttpServletRequest request) {
        String peer = request.getRemoteAddr();
        if (!isTrusted(peer)) {
            return peer;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (!StringUtils.hasText(forwarded)) {
            return peer;
        }
        String[] hops = forwarded.split(",");
        for (int i = hops.length - 1; i >= 0; i--) {
            String hop = hops[i].trim();
            if (!hop.isEmpty() && !isTrusted(hop)) {
                return hop;
            }
        }
        return peer;
    }

    private boolean isTrusted(String ip) {
        // Literal addresses only, so a forged hostname never triggers a DNS lookup.
        if (!StringUtils.hasText(ip) || !IP_LITERAL.matcher(ip).matches()) {
            return false;
        }
        try {
            return trustedProxies.stream().anyMatch(m -> m.matches(ip));
        } catch (IllegalArgumentException notAnIp) {
            return false;
        }
    }
}
