package com.linkflow.common.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Declares which immediate peers are allowed to assert a client's IP via forwarding headers.
 * <p>
 * Empty by default, which means no forwarding header is trusted at all. That is the safe
 * position: it is far better to rate limit a whole proxy as one client than to let any caller
 * mint an unlimited number of identities by inventing header values.
 */
@Component
@ConfigurationProperties(prefix = "linkflow.trusted-proxies")
@Getter
@Setter
public class TrustedProxyProperties {

    /**
     * CIDR ranges or single addresses of proxies permitted to set X-Forwarded-For / X-Real-IP.
     * Set this to the reverse proxy or load balancer sitting directly in front of the app —
     * never to a public range, which would defeat the check entirely.
     */
    private List<String> cidrs = new ArrayList<>();
}
