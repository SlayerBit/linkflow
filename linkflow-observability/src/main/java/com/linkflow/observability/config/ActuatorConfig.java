package com.linkflow.observability.config;

import org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Configuration;

/**
 * Ensures actuator-related beans from this module are loaded when observability is on the classpath.
 * Endpoint exposure (health, prometheus, metrics) is configured via application.yml.
 */
@Configuration
@ConditionalOnClass(WebEndpointProperties.class)
public class ActuatorConfig {
}
