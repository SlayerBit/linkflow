package com.linkflow.common.security;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ClientIpResolverTest {

    private static final String PROXY = "10.0.0.1";
    private static final String CLIENT = "203.0.113.9";

    @Nested
    class WithNoTrustedProxies {

        private final ClientIpResolver resolver = resolverTrusting();

        @Test
        void usesPeerAddress() {
            assertEquals(CLIENT, resolver.resolve(request(CLIENT, null)));
        }

        @Test
        void ignoresForwardedForSoRateLimitsCannotBeBypassed() {
            // The whole point: a direct caller inventing this header must not get a new identity.
            MockHttpServletRequest request = request(CLIENT, "1.2.3.4");

            assertEquals(CLIENT, resolver.resolve(request));
        }

        @Test
        void ignoresRealIp() {
            MockHttpServletRequest request = request(CLIENT, null);
            request.addHeader("X-Real-IP", "1.2.3.4");

            assertEquals(CLIENT, resolver.resolve(request));
        }
    }

    @Nested
    class WithTrustedProxy {

        private final ClientIpResolver resolver = resolverTrusting("10.0.0.0/8");

        @Test
        void honoursForwardedForFromTrustedPeer() {
            assertEquals(CLIENT, resolver.resolve(request(PROXY, CLIENT)));
        }

        @Test
        void ignoresForwardedForFromUntrustedPeer() {
            // Same header, but arriving directly rather than through the proxy.
            assertEquals("198.51.100.7", resolver.resolve(request("198.51.100.7", CLIENT)));
        }

        @Test
        void returnsRightmostUntrustedHopWhenClientForgedEntries() {
            // The client sent "1.2.3.4"; the proxy appended what it actually saw. Taking the
            // leftmost entry would return the forgery, so the rightmost untrusted hop wins.
            MockHttpServletRequest request = request(PROXY, "1.2.3.4, " + CLIENT);

            assertEquals(CLIENT, resolver.resolve(request));
        }

        @Test
        void skipsTrailingTrustedProxiesInChain() {
            MockHttpServletRequest request = request(PROXY, CLIENT + ", 10.0.0.5, 10.0.0.6");

            assertEquals(CLIENT, resolver.resolve(request));
        }

        @Test
        void fallsBackToPeerWhenEveryHopIsTrusted() {
            MockHttpServletRequest request = request(PROXY, "10.0.0.5, 10.0.0.6");

            assertEquals(PROXY, resolver.resolve(request));
        }

        @Test
        void fallsBackToRealIpWhenForwardedForAbsent() {
            MockHttpServletRequest request = request(PROXY, null);
            request.addHeader("X-Real-IP", CLIENT);

            assertEquals(CLIENT, resolver.resolve(request));
        }

        @Test
        void fallsBackToPeerWhenNoForwardingHeadersPresent() {
            assertEquals(PROXY, resolver.resolve(request(PROXY, null)));
        }

        @Test
        void toleratesBlankAndMalformedEntries() {
            MockHttpServletRequest request = request(PROXY, "not-an-ip, , " + CLIENT + ", ");

            assertEquals(CLIENT, resolver.resolve(request));
        }
    }

    @Test
    void rejectsInvalidCidrAtStartupRatherThanSilentlyTrustingNothing() {
        TrustedProxyProperties properties = new TrustedProxyProperties();
        properties.setCidrs(List.of("not-a-cidr"));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new ClientIpResolver(properties));
        assertTrue(ex.getMessage().contains("linkflow.trusted-proxies.cidrs"));
    }

    @Test
    void supportsSingleAddressWithoutPrefix() {
        ClientIpResolver resolver = resolverTrusting("172.18.0.5");

        assertEquals(CLIENT, resolver.resolve(request("172.18.0.5", CLIENT)));
    }

    private static ClientIpResolver resolverTrusting(String... cidrs) {
        TrustedProxyProperties properties = new TrustedProxyProperties();
        properties.setCidrs(List.of(cidrs));
        return new ClientIpResolver(properties);
    }

    private static MockHttpServletRequest request(String peer, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(peer);
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }
        return request;
    }
}
