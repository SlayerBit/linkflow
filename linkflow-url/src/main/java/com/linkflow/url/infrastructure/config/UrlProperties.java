package com.linkflow.url.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "linkflow")
public class UrlProperties {

    private String baseUrl = "http://localhost:8080";
}
