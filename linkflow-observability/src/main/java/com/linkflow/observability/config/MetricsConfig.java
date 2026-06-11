package com.linkflow.observability.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    @Bean
    public MeterBinder linkflowInfoMetrics(@Value("${spring.application.name:linkflow}") String applicationName) {
        return registry -> Gauge.builder("linkflow.info", () -> 1)
                .description("LinkFlow application presence metric for Prometheus discovery")
                .tag("application", applicationName)
                .register(registry);
    }

}
