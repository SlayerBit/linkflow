package com.linkflow.common.mail;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Minimum interval between transactional emails sent to the same address for the same purpose.
 * <p>
 * Deliberately a separate prefix from {@code linkflow.mail}, which belongs to linkflow-notification.
 * This throttle is enforced by the services that <em>request</em> mail, in linkflow-auth and
 * linkflow-user, and neither depends on the notification module — so binding into that module's
 * prefix would put one module's configuration under another's namespace.
 */
@Component
@ConfigurationProperties(prefix = "linkflow.mail-cooldown")
@Getter
@Setter
public class MailCooldownProperties {

    /**
     * How long an address must wait before the same kind of email can be sent to it again.
     * <p>
     * Set to zero to disable, which is only reasonable in tests that need to drive a flow twice in
     * quick succession.
     */
    private Duration interval = Duration.ofSeconds(60);
}
