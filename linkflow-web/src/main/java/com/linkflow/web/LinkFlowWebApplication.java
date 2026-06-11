package com.linkflow.web;

import com.linkflow.web.config.WebClientConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(WebClientConfig.class)
public class LinkFlowWebApplication {

    public static void main(String[] args) {
        SpringApplication.run(LinkFlowWebApplication.class, args);
    }
}
