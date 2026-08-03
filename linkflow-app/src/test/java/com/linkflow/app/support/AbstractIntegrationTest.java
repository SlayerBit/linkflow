package com.linkflow.app.support;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
public abstract class AbstractIntegrationTest {

        // Random 64-byte key; HS512 signing requires a 512-bit key.
        protected static final String JWT_SECRET =
                "XWoRZDqCxr8/uVuFLJbaEo4aSFjcxKj4Qqfu56MY1M+1KHv807/qYveB98YSjrmOtooV/kR+D050WjbweBk0fg==";

        protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
                        .withDatabaseName("linkflow")
                        .withUsername("linkflow")
                        .withPassword("linkflow");

        protected static final String REDIS_PASSWORD = "test-redis-password";

        // Password-protected on purpose: production requires Redis auth, so the tests should
        // exercise the authenticated path rather than a more permissive setup than we ship.
        protected static final GenericContainer<?> REDIS = new GenericContainer<>(
                        DockerImageName.parse("redis:7-alpine"))
                        .withExposedPorts(6379)
                        .withCommand("redis-server", "--requirepass", REDIS_PASSWORD);

        static {
                if (org.testcontainers.DockerClientFactory.instance().isDockerAvailable()) {
                        POSTGRES.start();
                        REDIS.start();
                        TestMailbox.start();
                }
        }

        @DynamicPropertySource
        static void configureProperties(DynamicPropertyRegistry registry) {
                registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
                registry.add("spring.datasource.username", POSTGRES::getUsername);
                registry.add("spring.datasource.password", POSTGRES::getPassword);
                registry.add("spring.data.redis.host", REDIS::getHost);
                registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
                registry.add("spring.data.redis.password", () -> REDIS_PASSWORD);
                registry.add("linkflow.jwt.secret", () -> JWT_SECRET);
                // Shaped like a real deployment so tests that activate the prod profile satisfy
                // ProductionConfigValidator rather than working around it.
                registry.add("linkflow.base-url", () -> "https://linkflow.test");
                registry.add("linkflow.cors.allowed-origins", () -> "https://app.linkflow.test");
                // Deliver to the in-JVM SMTP server so tests can read verification links the same
                // way a user would, rather than relying on tokens leaking through the API.
                registry.add("spring.mail.host", () -> "127.0.0.1");
                registry.add("spring.mail.port", TestMailbox::port);
                registry.add("linkflow.mail.base-url", () -> "https://linkflow.test");
                registry.add("linkflow.mail.from-address", () -> "no-reply@linkflow.test");
                // Recovery flows are driven several times in a row here, which a real user would
                // not do inside a minute. The cooldown itself is covered by MailSendCooldownIT
                // against the same Redis container, so switching it off costs no coverage.
                registry.add("linkflow.mail-cooldown.interval", () -> "0s");
        }

        @Autowired
        protected MockMvc mockMvc;

        @BeforeEach
        void clearMailbox() {
                TestMailbox.clear();
        }

        /**
         * Registers a user and completes activation by following the emailed verification link,
         * leaving the account able to log in.
         */
        protected String registerUser(String email, String password, String firstName) throws Exception {
                String json = register(email, password, firstName);

                String token = TestMailbox.awaitToken(email, "/verify-email");
                mockMvc.perform(post("/api/v1/auth/verify-email")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"token\":\"" + token + "\"}"))
                                .andExpect(status().isOk());

                return json;
        }

        /**
         * Registers without activating, for tests that need an unverified account.
         */
        protected String register(String email, String password, String firstName) throws Exception {
                String body = """
                                {
                                  "email": "%s",
                                  "password": "%s",
                                  "firstName": "%s",
                                  "lastName": "Test"
                                }
                                """.formatted(email, password, firstName);

                MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                                .andReturn();
                return result.getResponse().getContentAsString();
        }

        protected TokenPair login(String email, String password) throws Exception {
                String body = """
                                {
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(email, password);

                MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                                .andReturn();

                String json = result.getResponse().getContentAsString();
                return new TokenPair(
                                JsonPath.read(json, "$.data.accessToken"),
                                JsonPath.read(json, "$.data.refreshToken"));
        }

        protected record TokenPair(String accessToken, String refreshToken) {
        }
}
