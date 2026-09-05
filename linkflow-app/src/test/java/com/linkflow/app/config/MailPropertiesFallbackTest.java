package com.linkflow.app.config;

import com.linkflow.notification.config.MailProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests property binding and fallback semantics for linkflow.mail.base-url.
 * Ensures the configuration chain properly resolves LINKFLOW_MAIL_BASE_URL -> linkflow.mail.base-url
 * and falls back to linkflow.base-url (LINKFLOW_BASE_URL) when unset.
 */
class MailPropertiesFallbackTest {

    @Configuration
    @EnableConfigurationProperties(MailProperties.class)
    static class TestConfig {}

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    void whenMailBaseUrlExplicitlyConfigured_itIsUsed() {
        runner.withPropertyValues(
                "linkflow.base-url=https://other.example.com",
                "linkflow.mail.base-url=https://linkflow.slayerbit.me"
        ).run(context -> {
            assertThat(context).hasNotFailed();
            MailProperties props = context.getBean(MailProperties.class);
            assertThat(props.getBaseUrl()).isEqualTo("https://linkflow.slayerbit.me");
        });
    }

    @Test
    void whenMailBaseUrlUsesFallbackExpression_itResolvesToApplicationBaseUrl() {
        runner.withPropertyValues(
                "linkflow.base-url=https://linkflow.slayerbit.me",
                "linkflow.mail.base-url=${linkflow.base-url}"
        ).run(context -> {
            assertThat(context).hasNotFailed();
            MailProperties props = context.getBean(MailProperties.class);
            assertThat(props.getBaseUrl()).isEqualTo("https://linkflow.slayerbit.me");
        });
    }

    @Test
    void whenMailBaseUrlUnset_itDefaultsToLocalhost() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            MailProperties props = context.getBean(MailProperties.class);
            assertThat(props.getBaseUrl()).isEqualTo("http://localhost:8080");
        });
    }
}
