package com.linkflow.notification.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Refuses to start a production instance whose mail configuration would silently break
 * account recovery.
 * <p>
 * Email verification and password reset are the only paths by which a user can gain or regain
 * access to an account. A prod instance with unreachable SMTP is therefore not a degraded
 * deployment but a broken one, and it is far better to fail at startup than to discover it when
 * a locked-out user files a support ticket.
 * <p>
 * Validation runs in {@link InitializingBean#afterPropertiesSet()} so the process exits before
 * the web server binds a port and starts accepting traffic.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MailConfigValidator implements InitializingBean {

    private static final String PROD_PROFILE = "prod";
    private static final String PLACEHOLDER_DOMAIN = "@linkflow.local";

    private final MailProperties mailProperties;
    private final Environment environment;

    @Override
    public void afterPropertiesSet() {
        String smtpHost = environment.getProperty("spring.mail.host", "");

        if (!isProdProfileActive()) {
            logNonProdConfiguration(smtpHost);
            return;
        }

        List<String> problems = collectProdProblems(smtpHost);
        if (!problems.isEmpty()) {
            throw new IllegalStateException(
                    "Invalid production mail configuration — account verification and password reset "
                            + "would be non-functional:\n  - " + String.join("\n  - ", problems)
                            + "\nSee docs/DEPLOYMENT.md for SMTP provider setup.");
        }

        log.info("Mail configuration validated: host={}, from={}, baseUrl={}",
                smtpHost, mailProperties.getFromAddress(), mailProperties.getBaseUrl());
    }

    private List<String> collectProdProblems(String smtpHost) {
        List<String> problems = new ArrayList<>();

        if (!mailProperties.isEnabled()) {
            problems.add("linkflow.mail.enabled is false; set LINKFLOW_MAIL_ENABLED=true "
                    + "(mail may only be disabled outside production)");
        }
        if (smtpHost.isBlank()) {
            problems.add("spring.mail.host is not set; set SPRING_MAIL_HOST");
        }
        if (mailProperties.getFromAddress().endsWith(PLACEHOLDER_DOMAIN)) {
            problems.add("linkflow.mail.from-address still uses the placeholder domain "
                    + PLACEHOLDER_DOMAIN + "; set LINKFLOW_MAIL_FROM to a deliverable address on a "
                    + "domain you control (SPF/DKIM must permit your SMTP relay)");
        }

        String baseUrl = mailProperties.getBaseUrl();
        if (!baseUrl.startsWith("https://")) {
            problems.add("linkflow.mail.base-url must use https in production (got '" + baseUrl
                    + "'); verification links carry single-use tokens and must not travel over plaintext");
        }
        if (baseUrl.contains("localhost") || baseUrl.contains("127.0.0.1")) {
            problems.add("linkflow.mail.base-url points at localhost ('" + baseUrl
                    + "'); recipients cannot resolve it — set LINKFLOW_MAIL_BASE_URL to the public origin");
        }

        return problems;
    }

    private void logNonProdConfiguration(String smtpHost) {
        if (!mailProperties.isEnabled()) {
            log.warn("Mail delivery is DISABLED (linkflow.mail.enabled=false). Verification and "
                    + "password-reset messages will be logged and discarded, not sent.");
            return;
        }
        if (smtpHost.isBlank()) {
            log.warn("Mail is enabled but spring.mail.host is unset; delivery will fail. "
                    + "Start the dev SMTP catcher with: docker compose --profile dev up -d mailhog");
            return;
        }
        log.info("Mail configuration (non-prod): host={}, from={}, baseUrl={}",
                smtpHost, mailProperties.getFromAddress(), mailProperties.getBaseUrl());
    }

    private boolean isProdProfileActive() {
        for (String profile : environment.getActiveProfiles()) {
            if (PROD_PROFILE.equalsIgnoreCase(profile)) {
                return true;
            }
        }
        return false;
    }
}
