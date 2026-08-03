package com.linkflow.notification.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for outbound transactional email.
 * <p>
 * SMTP connection settings themselves live under {@code spring.mail.*} and are handled by
 * Spring Boot's mail auto-configuration; this class covers only LinkFlow-specific concerns.
 */
@Component
@ConfigurationProperties(prefix = "linkflow.mail")
@Getter
@Setter
public class MailProperties {

    /**
     * When false, mail is logged at INFO and discarded instead of being sent. Intended only for
     * environments with no SMTP reachability, such as CI unit-test runs. Rejected in the prod
     * profile by {@link MailConfigValidator}.
     */
    private boolean enabled = true;

    /** Envelope and header From address. */
    private String fromAddress = "no-reply@linkflow.local";

    /** Display name shown alongside the From address. */
    private String fromName = "LinkFlow";

    /**
     * Public base URL used to build links in emails. Must be the address a recipient's browser
     * can reach — the gateway or Nginx origin, not an internal container hostname.
     */
    private String baseUrl = "http://localhost:8080";

    /** Total delivery attempts per message, including the first. */
    private int maxAttempts = 3;

    /** Base backoff between delivery attempts; doubles on each retry. */
    private long retryBackoffMs = 1000;
}
