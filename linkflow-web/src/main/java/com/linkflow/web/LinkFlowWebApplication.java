package com.linkflow.web;

import com.linkflow.web.config.WebClientConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Excludes {@link UserDetailsServiceAutoConfiguration} because this module holds no credentials.
 * Sign-in is delegated to the backend API and the resulting tokens are kept server-side in the
 * session, so there is nothing for Spring Security to authenticate against locally.
 * <p>
 * Left in place, that auto-configuration creates an in-memory {@code user} with a random password
 * and logs it at WARN on every start, advising that the security configuration must be changed
 * before production. Both form login and HTTP Basic are disabled here, so the account is
 * unreachable — the only thing it produces is a production warning about a non-issue, which is
 * exactly the kind of noise that trains operators to ignore startup logs.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableConfigurationProperties(WebClientConfig.class)
public class LinkFlowWebApplication {

    public static void main(String[] args) {
        SpringApplication.run(LinkFlowWebApplication.class, args);
    }
}
