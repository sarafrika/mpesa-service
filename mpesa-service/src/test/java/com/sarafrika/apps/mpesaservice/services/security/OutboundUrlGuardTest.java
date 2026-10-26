package com.sarafrika.apps.mpesaservice.services.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Product-supplied URLs reach only public https hosts, whatever their name resolves to. */
class OutboundUrlGuardTest {

    private static final Map<String, String> DNS = Map.of(
            "hooks.elimika.co.ke", "41.90.64.10",
            "sneaky.example.com", "10.0.0.5",
            "metadata.example.com", "169.254.169.254",
            "v6.example.com", "fd12:3456::1");

    /** Literal IPs resolve to themselves; names come from the table above, as a DNS server would answer. */
    private static InetAddress[] resolve(String host) throws UnknownHostException {
        String ip = DNS.get(host);
        if (ip == null && !host.matches("[0-9.]+|.*:.*")) {
            throw new UnknownHostException(host);
        }
        return new InetAddress[]{InetAddress.getByName(ip == null ? host : ip)};
    }

    private final OutboundUrlGuard guard = new OutboundUrlGuard(OutboundUrlGuardTest::resolve, false);
    private final OutboundUrlGuard dev = new OutboundUrlGuard(OutboundUrlGuardTest::resolve, true);

    @ParameterizedTest
    @ValueSource(strings = {
            "https://127.0.0.1/hook", "https://[::1]/hook", "https://10.1.2.3/hook", "https://172.16.0.1/hook",
            "https://172.31.255.255/hook", "https://192.168.1.10/hook", "https://100.64.0.1/hook",
            "https://100.127.255.254/hook", "https://169.254.169.254/latest/meta-data", "https://[fd00::1]/hook",
            "https://[fe80::1]/hook", "https://0.0.0.0/hook", "https://[::ffff:10.0.0.1]/hook",
            "https://sneaky.example.com/hook", "https://metadata.example.com/hook", "https://v6.example.com/hook"})
    void internalAddressesAreRefused(String url) {
        assertThatThrownBy(() -> guard.check(url)).isInstanceOf(OutboundUrlGuard.BlockedUrlException.class)
                .hasMessageContaining("public address");
    }

    @Test
    void aPublicHttpsHostIsAllowed() {
        assertThat(guard.check("https://hooks.elimika.co.ke/mpesa")).isEqualTo(URI.create("https://hooks.elimika.co.ke/mpesa"));
        assertThat(guard.check("https://172.32.0.1/hook")).isNotNull();
        assertThat(guard.check("https://100.128.0.1/hook")).isNotNull();
    }

    @Test
    void schemeCredentialsAndUnknownHostsAreRefused() {
        assertThatThrownBy(() -> guard.check("http://hooks.elimika.co.ke/mpesa")).hasMessageContaining("https");
        assertThatThrownBy(() -> guard.check("https://user:pw@hooks.elimika.co.ke/x")).hasMessageContaining("credentials");
        assertThatThrownBy(() -> guard.check("https://nowhere.invalid/x")).hasMessageContaining("does not resolve");
        assertThatThrownBy(() -> guard.check("not a url")).isInstanceOf(OutboundUrlGuard.BlockedUrlException.class);
    }

    @Test
    void localhostOnlyWithTheDevelopmentFlag() {
        assertThatThrownBy(() -> guard.check("http://localhost:4000/hook"))
                .isInstanceOf(OutboundUrlGuard.BlockedUrlException.class);
        assertThat(dev.check("http://localhost:4000/hook")).isNotNull();
        assertThat(dev.check("http://127.0.0.1:4000/hook")).isNotNull();
        assertThatThrownBy(() -> dev.check("https://10.1.2.3/hook"))
                .isInstanceOf(OutboundUrlGuard.BlockedUrlException.class);
    }
}
