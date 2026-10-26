package com.sarafrika.apps.mpesaservice.services.security;

import java.net.InetAddress;

/** Guards for tests, which must not depend on real DNS. */
public final class TestUrlGuards {

    private TestUrlGuards() {
    }

    /** Every host name resolves to one public address (literal IPs stay as they are). */
    public static OutboundUrlGuard publicDns() {
        return new OutboundUrlGuard(host -> new InetAddress[]{
                InetAddress.getByName(host.matches("[0-9.]+|.*:.*") ? host : "41.90.64.10")}, false);
    }
}
