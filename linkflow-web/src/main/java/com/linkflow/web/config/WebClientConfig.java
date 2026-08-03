package com.linkflow.web.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
@ConfigurationProperties(prefix = "linkflow.web")
@Getter
@Setter
public class WebClientConfig {

    private String gatewayUrl = "http://127.0.0.1:8080";
    private String publicGatewayUrl = "http://localhost:8080";
    private String backendAppUrl = "http://127.0.0.1:8081";
    private String grafanaUrl = "http://localhost:3000";
    private String prometheusUrl = "http://localhost:9090";
    private RateLimit rateLimit = new RateLimit();
    private Timeouts timeouts = new Timeouts();

    @Getter
    @Setter
    public static class RateLimit {
        private int userRpm = 100;
        private int ipRpm = 200;
    }

    /**
     * Bounds on calls to the backend.
     * <p>
     * Without them a backend that accepts connections but never answers would hold a web request
     * thread indefinitely. Enough slow calls and the servlet thread pool is exhausted, so an
     * unresponsive backend takes the UI down with it instead of surfacing an error page.
     */
    @Getter
    @Setter
    public static class Timeouts {
        /** Establishing the TCP connection; should be fast on a local network. */
        private Duration connect = Duration.ofSeconds(2);

        /**
         * Waiting for the response. Generous enough for the slowest legitimate call (analytics
         * aggregation) while still bounded.
         */
        private Duration read = Duration.ofSeconds(10);
    }

    @Bean
    public RestClient restClient() {
        return RestClient.builder()
                .baseUrl(gatewayUrl)
                .requestFactory(timeoutRequestFactory())
                .build();
    }

    @Bean
    public RestClient backendRestClient() {
        return RestClient.builder()
                .baseUrl(backendAppUrl)
                .requestFactory(timeoutRequestFactory())
                .build();
    }

    private ClientHttpRequestFactory timeoutRequestFactory() {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(timeouts.getConnect())
                .withReadTimeout(timeouts.getRead());
        return ClientHttpRequestFactoryBuilder.detect().build(settings);
    }
}
