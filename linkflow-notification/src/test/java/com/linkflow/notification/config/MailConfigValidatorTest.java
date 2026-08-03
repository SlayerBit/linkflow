package com.linkflow.notification.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MailConfigValidatorTest {

    private MailProperties properties;

    @BeforeEach
    void setUp() {
        properties = new MailProperties();
        properties.setEnabled(true);
        properties.setFromAddress("no-reply@linkflow.example");
        properties.setBaseUrl("https://links.example.com");
    }

    @Test
    void validProdConfigurationStarts() {
        assertThatCode(() -> validate(prodEnvironment("smtp.example.com")))
                .doesNotThrowAnyException();
    }

    @Test
    void prodRejectsMissingSmtpHost() {
        assertThatThrownBy(() -> validate(prodEnvironment("")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.mail.host is not set");
    }

    @Test
    void prodRejectsDisabledMail() {
        properties.setEnabled(false);

        assertThatThrownBy(() -> validate(prodEnvironment("smtp.example.com")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("linkflow.mail.enabled is false");
    }

    @Test
    void prodRejectsPlaceholderFromAddress() {
        properties.setFromAddress("no-reply@linkflow.local");

        assertThatThrownBy(() -> validate(prodEnvironment("smtp.example.com")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("placeholder domain");
    }

    @Test
    void prodRejectsPlaintextBaseUrl() {
        properties.setBaseUrl("http://links.example.com");

        assertThatThrownBy(() -> validate(prodEnvironment("smtp.example.com")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must use https");
    }

    @Test
    void prodRejectsLocalhostBaseUrlRecipientsCannotResolve() {
        properties.setBaseUrl("https://localhost:8080");

        assertThatThrownBy(() -> validate(prodEnvironment("smtp.example.com")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("points at localhost");
    }

    @Test
    void reportsEveryProblemAtOnceRatherThanOnePerRestart() {
        properties.setFromAddress("no-reply@linkflow.local");
        properties.setBaseUrl("http://localhost:8080");

        assertThatThrownBy(() -> validate(prodEnvironment("")))
                .isInstanceOf(IllegalStateException.class)
                .satisfies(e -> assertThat(e.getMessage())
                        .contains("spring.mail.host is not set")
                        .contains("placeholder domain")
                        .contains("must use https")
                        .contains("points at localhost"));
    }

    @Test
    void nonProdToleratesLocalDevelopmentDefaults() {
        properties.setEnabled(false);
        properties.setFromAddress("no-reply@linkflow.local");
        properties.setBaseUrl("http://localhost:8080");

        MockEnvironment dev = new MockEnvironment();
        dev.setActiveProfiles("dev");

        // Local development must not be blocked by production-only requirements.
        assertThatCode(() -> validate(dev)).doesNotThrowAnyException();
    }

    @Test
    void dockerDemoProfileIsNotTreatedAsProduction() {
        properties.setFromAddress("no-reply@linkflow.local");
        properties.setBaseUrl("http://localhost:8080");

        MockEnvironment docker = new MockEnvironment();
        docker.setActiveProfiles("docker");
        docker.setProperty("spring.mail.host", "mailhog");

        assertThatCode(() -> validate(docker)).doesNotThrowAnyException();
    }

    private void validate(MockEnvironment environment) {
        new MailConfigValidator(properties, environment).afterPropertiesSet();
    }

    private MockEnvironment prodEnvironment(String smtpHost) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        environment.setProperty("spring.mail.host", smtpHost);
        return environment;
    }
}
