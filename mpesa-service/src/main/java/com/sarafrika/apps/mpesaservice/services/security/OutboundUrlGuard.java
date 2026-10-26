package com.sarafrika.apps.mpesaservice.services.security;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Keeps calls to product-supplied URLs (webhooks, C2B account lookups) off this network: https only, and
 * every address the host resolves to must be public. Checked when a URL is saved and again before each
 * call, so a name later re-pointed at an internal address is still refused. */
@Component
public class OutboundUrlGuard {

    private static final Set<String> LOCAL_NAMES = Set.of("localhost", "127.0.0.1", "::1", "[::1]");

    /** Name resolution, replaceable in tests. */
    @FunctionalInterface
    public interface Resolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    /** The URL may not be called; the message is safe to show to the person who entered it. */
    public static class BlockedUrlException extends RuntimeException {
        public BlockedUrlException(String message) {
            super(message);
        }
    }

    private final Resolver resolver;
    private final boolean allowLocalhost;

    @Autowired
    public OutboundUrlGuard(@Value("${mpesa.outbound.allow-localhost:false}") boolean allowLocalhost) {
        this(InetAddress::getAllByName, allowLocalhost);
    }

    public OutboundUrlGuard(Resolver resolver, boolean allowLocalhost) {
        this.resolver = resolver;
        this.allowLocalhost = allowLocalhost;
    }

    public URI check(String url) {
        try {
            return check(new URI(url.trim()));
        } catch (java.net.URISyntaxException | NullPointerException e) {
            throw new BlockedUrlException("is not a valid URL");
        }
    }

    public URI check(URI uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (host.isEmpty()) {
            throw new BlockedUrlException("must be an absolute URL with a host");
        }
        if (uri.getUserInfo() != null) {
            throw new BlockedUrlException("must not contain credentials");
        }
        boolean devLocal = allowLocalhost && LOCAL_NAMES.contains(host);
        if (!scheme.equals("https") && !(devLocal && scheme.equals("http"))) {
            throw new BlockedUrlException("must use https");
        }
        if (devLocal) {
            return uri;
        }
        InetAddress[] addresses;
        try {
            addresses = resolver.resolve(host.startsWith("[") ? host.substring(1, host.length() - 1) : host);
        } catch (UnknownHostException e) {
            throw new BlockedUrlException("has a host name that does not resolve");
        }
        if (addresses == null || addresses.length == 0) {
            throw new BlockedUrlException("has a host name that does not resolve");
        }
        for (InetAddress address : addresses) {
            if (isInternal(address)) {
                throw new BlockedUrlException("must point to a public address, not a private or internal one");
            }
        }
        return uri;
    }

    /** Loopback, private, link-local (incl. cloud metadata 169.254.169.254), CGNAT, unique-local and the like. */
    static boolean isInternal(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return true;
        }
        byte[] b = address.getAddress();
        if (address instanceof Inet4Address) {
            return isInternalV4(b);
        }
        if (address instanceof Inet6Address) {
            if ((b[0] & 0xfe) == 0xfc) {
                return true; // fc00::/7 unique local, fd00::/8 among them
            }
            boolean mapped = true;
            for (int i = 0; i < 10; i++) {
                mapped &= b[i] == 0;
            }
            if (mapped && (b[10] & 0xff) == 0xff && (b[11] & 0xff) == 0xff) {
                return isInternalV4(new byte[]{b[12], b[13], b[14], b[15]}); // ::ffff:a.b.c.d
            }
        }
        return false;
    }

    private static boolean isInternalV4(byte[] b) {
        int a = b[0] & 0xff;
        int second = b[1] & 0xff;
        return a == 0 || a == 10 || a == 127 || a >= 224
                || (a == 100 && second >= 64 && second <= 127)   // 100.64.0.0/10 carrier-grade NAT
                || (a == 169 && second == 254)
                || (a == 172 && second >= 16 && second <= 31)
                || (a == 192 && second == 168)
                || (a == 192 && second == 0 && (b[2] & 0xff) == 0)
                || (a == 198 && (second == 18 || second == 19));
    }
}
