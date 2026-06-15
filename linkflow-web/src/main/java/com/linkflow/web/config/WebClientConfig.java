package com.linkflow.web.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@ConfigurationProperties(prefix = "linkflow.web")
@Getter
@Setter
public class WebClientConfig {

    private String gatewayUrl = "http://127.0.0.1:8080";
    private String publicGatewayUrl = "http://localhost:8080";
    private String grafanaUrl = "http://localhost:3000";
    private String prometheusUrl = "http://localhost:9090";
    private RateLimit rateLimit = new RateLimit();

    @Getter
    @Setter
    public static class RateLimit {
        private int userRpm = 100;
        private int ipRpm = 200;
    }

    @Bean
    public RestClient restClient() {
        return RestClient.builder()
                .baseUrl(gatewayUrl)
                .build();
    }
}
