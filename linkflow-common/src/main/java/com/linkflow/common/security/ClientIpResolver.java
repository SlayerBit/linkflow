package com.linkflow.common.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Determines the originating client IP for rate limiting and analytics.
 * <p>
 * Forwarding headers are attacker-controlled data: anyone can send
 * {@code X-Forwarded-For: 1.2.3.4}. Honouring them unconditionally lets a single caller present
 * a new identity on every request and bypass IP rate limiting entirely. They are therefore only
 * consulted when the immediate peer is a configured trusted proxy.
 * <p>
 * When the peer is trusted, the chain is walked from right to left and the first address that is
 * not itself a trusted proxy is returned. Entries are appended left to right as a request passes
 * through proxies, so anything a client forged sits to the left of the addresses its own proxies
 * observed — scanning from the right skips the forgeable portion.
 */
@Slf4j
@Component
public class ClientIpResolver {

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String X_REAL_IP = "X-Real-IP";

    private final List<IpAddressMatcher> trustedProxyMatchers;
    private final boolean forwardingHeadersTrusted;

    public ClientIpResolver(TrustedProxyProperties properties) {
        this.trustedProxyMatchers = compile(properties.getCidrs());
        this.forwardingHeadersTrusted = !trustedProxyMatchers.isEmpty();

        if (forwardingHeadersTrusted) {
            log.info("Trusting forwarding headers from {} proxy range(s): {}",
                    trustedProxyMatchers.size(), properties.getCidrs());
        } else {
            log.info("No trusted proxies configured; forwarding headers are ignored and the direct "
                    + "peer address is used. Set linkflow.trusted-proxies.cidrs when running behind "
                    + "a reverse proxy, otherwise all traffic is attributed to the proxy.");
        }
    }

    /**
     * @return the client address to attribute this request to; never null
     */
    public String resolve(HttpServletRequest request) {
        String peer = request.getRemoteAddr();

        if (!forwardingHeadersTrusted || !isTrustedProxy(peer)) {
            return peer;
        }

        String forwardedFor = request.getHeader(X_FORWARDED_FOR);
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            String candidate = rightmostUntrusted(forwardedFor);
            if (candidate != null) {
                return candidate;
            }
        }

        String realIp = request.getHeader(X_REAL_IP);
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }

        return peer;
    }

    /**
     * @return the nearest address in the chain that is not a trusted proxy, or null if every hop
     *         is trusted or the header holds nothing usable
     */
    private String rightmostUntrusted(String forwardedForHeader) {
        String[] hops = forwardedForHeader.split(",");

        for (int i = hops.length - 1; i >= 0; i--) {
            String hop = hops[i].trim();
            if (hop.isEmpty()) {
                continue;
            }
            if (!isTrustedProxy(hop)) {
                return hop;
            }
        }
        return null;
    }

    private boolean isTrustedProxy(String address) {
        for (IpAddressMatcher matcher : trustedProxyMatchers) {
            try {
                if (matcher.matches(address)) {
                    return true;
                }
            } catch (IllegalArgumentException e) {
                // Malformed value in the header; treat as untrusted rather than failing the request.
                return false;
            }
        }
        return false;
    }

    private static List<IpAddressMatcher> compile(List<String> cidrs) {
        List<IpAddressMatcher> matchers = new ArrayList<>();
        for (String cidr : cidrs) {
            if (cidr == null || cidr.isBlank()) {
                continue;
            }
            try {
                matchers.add(new IpAddressMatcher(cidr.trim()));
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException(
                        "Invalid CIDR in linkflow.trusted-proxies.cidrs: '" + cidr
                                + "'. Use forms like 10.0.0.0/8, 172.18.0.5, or ::1/128.", e);
            }
        }
        return matchers;
    }
}
