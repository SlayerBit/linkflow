package com.linkflow.auth.bootstrap;

import com.linkflow.common.port.UserLookupPort;
import com.linkflow.common.port.UserLookupPort.CreateUserCommand;
import com.linkflow.common.security.SecurityConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;

/**
 * Bootstrap mechanism for creating the first admin user.
 * Controlled by environment variables. Idempotent — skips if user already exists.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {

    private static final int MIN_PROD_PASSWORD_LENGTH = 12;

    private final UserLookupPort userLookupPort;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;

    @Value("${linkflow.bootstrap.admin.enabled:false}")
    private boolean enabled;

    @Value("${linkflow.bootstrap.admin.email:}")
    private String adminEmail;

    @Value("${linkflow.bootstrap.admin.password:}")
    private String adminPassword;

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.debug("Admin bootstrap is disabled");
            return;
        }

        if (isBlank(adminEmail) || isBlank(adminPassword)) {
            // Silently skipping would leave an operator believing an admin exists when it does not,
            // and they would discover otherwise only when locked out of the admin area.
            throw new IllegalStateException(
                    "Admin bootstrap is enabled but LINKFLOW_BOOTSTRAP_ADMIN_EMAIL and "
                            + "LINKFLOW_BOOTSTRAP_ADMIN_PASSWORD are not both set. Set them, or set "
                            + "LINKFLOW_BOOTSTRAP_ADMIN_ENABLED=false.");
        }

        if (isProd() && adminPassword.length() < MIN_PROD_PASSWORD_LENGTH) {
            throw new IllegalStateException(
                    "LINKFLOW_BOOTSTRAP_ADMIN_PASSWORD must be at least " + MIN_PROD_PASSWORD_LENGTH
                            + " characters in the prod profile. This account holds full "
                            + "administrative access and is a standing target for credential guessing.");
        }

        if (userLookupPort.existsByEmail(adminEmail)) {
            log.info("Admin user already exists: {}. Skipping bootstrap.", adminEmail);
            return;
        }

        String hashedPassword = passwordEncoder.encode(adminPassword);
        CreateUserCommand command = new CreateUserCommand(
                adminEmail,
                hashedPassword,
                "Admin",
                "User",
                Set.of(SecurityConstants.ROLE_USER, SecurityConstants.ROLE_ADMIN),
                true
        );

        var admin = userLookupPort.createUser(command);
        userLookupPort.updateEmailVerified(admin.id(), true);
        log.info("Bootstrap admin user created and verified: {}. Disable admin bootstrap "
                + "(LINKFLOW_BOOTSTRAP_ADMIN_ENABLED=false) and remove the password from the "
                + "environment now that the account exists.", adminEmail);
    }

    private boolean isProd() {
        return Arrays.asList(environment.getActiveProfiles()).contains("prod");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
