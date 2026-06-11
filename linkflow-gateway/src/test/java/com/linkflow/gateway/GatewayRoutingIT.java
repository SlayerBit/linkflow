package com.linkflow.gateway;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayRoutingIT {

    private static final MockWebServer appServer = new MockWebServer();
    private static final MockWebServer webServer = new MockWebServer();

    static {
        try {
            appServer.start();
            webServer.start();
        } catch (IOException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    @Autowired
    private WebTestClient webTestClient;

    @DynamicPropertySource
    static void registerUris(DynamicPropertyRegistry registry) {
        registry.add("LINKFLOW_APP_URI", () -> baseUrl(appServer));
        registry.add("LINKFLOW_WEB_URI", () -> baseUrl(webServer));
    }

    @AfterAll
    static void shutdownServers() throws IOException {
        appServer.shutdown();
        webServer.shutdown();
    }

    @Test
    void routesApiRequestsToBackendApp() throws InterruptedException {
        appServer.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true}"));

        webTestClient.get()
                .uri("/api/v1/analytics/top")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("{\"ok\":true}");

        assertEquals("/api/v1/analytics/top", appServer.takeRequest().getPath());
    }

    @Test
    void routesRedirectRequestsToBackendApp() throws InterruptedException {
        appServer.enqueue(new MockResponse().setResponseCode(302).addHeader("Location", "https://example.com"));

        webTestClient.get()
                .uri("/r/demo")
                .exchange()
                .expectStatus().isFound()
                .expectHeader().valueEquals("Location", "https://example.com");

        assertEquals("/r/demo", appServer.takeRequest().getPath());
    }

    @Test
    void routesRootPathToWebUi() throws InterruptedException {
        webServer.enqueue(new MockResponse().setResponseCode(200).setBody("<html>LinkFlow</html>"));

        webTestClient.get()
                .uri("/")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).value(body -> org.junit.jupiter.api.Assertions.assertTrue(body.contains("LinkFlow")));

        assertEquals("/", webServer.takeRequest().getPath());
    }

    @Test
    void routesStaticAssetsToWebUi() throws InterruptedException {
        webServer.enqueue(new MockResponse().setResponseCode(200).setBody("body { }"));

        webTestClient.get()
                .uri("/css/custom.css")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("body { }");

        assertEquals("/css/custom.css", webServer.takeRequest().getPath());
    }

    @Test
    void exposesGatewayHealthWithoutProxyingToBackend() {
        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP");

        assertEquals(0, appServer.getRequestCount());
        assertEquals(0, webServer.getRequestCount());
    }

    private static String baseUrl(MockWebServer server) {
        return server.url("/").toString().replaceAll("/$", "");
    }
}
