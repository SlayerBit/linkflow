package com.linkflow.auth.bootstrap;

import com.linkflow.common.port.UserLookupPort;
import com.linkflow.common.port.UserLookupPort.CreateUserCommand;
import com.linkflow.common.security.SecurityConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Bootstrap mechanism for creating the first admin user.
 * Controlled by environment variables. Idempotent — skips if user already exists.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {

    private final UserLookupPort userLookupPort;
    private final PasswordEncoder passwordEncoder;

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

        if (adminEmail == null || adminEmail.isBlank() || adminPassword == null || adminPassword.isBlank()) {
            log.warn("Admin bootstrap enabled but email/password not configured. Skipping.");
            return;
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
                Set.of(SecurityConstants.ROLE_USER, SecurityConstants.ROLE_ADMIN)
        );

        userLookupPort.createUser(command);
        log.info("Bootstrap admin user created: {}", adminEmail);
    }
}
