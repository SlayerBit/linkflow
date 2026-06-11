package com.linkflow.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.linkflow")
@EnableJpaAuditing
@EnableJpaRepositories(basePackages = "com.linkflow")
@EntityScan(basePackages = "com.linkflow")
@EnableAsync
@EnableScheduling
public class LinkFlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(LinkFlowApplication.class, args);
    }
}
