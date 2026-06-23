package com.linkflow.auth.infrastructure.security;

import com.linkflow.auth.infrastructure.config.LinkflowSecurityProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;
import jakarta.servlet.Filter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String[] SWAGGER_PATHS = {
            "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/v3/api-docs.yaml"
    };

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint authenticationEntryPoint;
    private final CustomAccessDeniedHandler accessDeniedHandler;
    private final LinkflowSecurityProperties securityProperties;
    private final CorsConfigurationSource corsConfigurationSource;

    @Autowired(required = false)
    @Qualifier("rateLimitFilter")
    private Filter rateLimitFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(
                            "/api/v1/auth/register",
                            "/api/v1/auth/login",
                            "/api/v1/auth/refresh",
                            "/api/v1/auth/logout",
                            "/api/v1/auth/verify-email",
                            "/api/v1/auth/forgot-password",
                            "/api/v1/auth/reset-password",
                            "/api/v1/users/verify-email-change")
                            .permitAll()
                            .requestMatchers("/r/**").permitAll();

                    configureSwaggerAccess(auth);
                    configureActuatorAccess(auth);

                    auth.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                            .anyRequest().authenticated();
                })
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        if (rateLimitFilter != null) {
            http.addFilterAfter(rateLimitFilter, JwtAuthenticationFilter.class);
        }

        return http.build();
    }

    private void configureSwaggerAccess(
            org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer<?>.AuthorizationManagerRequestMatcherRegistry auth) {
        if (securityProperties.isSwaggerPublic()) {
            auth.requestMatchers(SWAGGER_PATHS).permitAll();
        } else {
            auth.requestMatchers(SWAGGER_PATHS).denyAll();
        }
    }

    private void configureActuatorAccess(
            org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer<?>.AuthorizationManagerRequestMatcherRegistry auth) {
        if (securityProperties.isActuatorPublic()) {
            auth.requestMatchers("/actuator/**").permitAll();
            return;
        }

        auth.requestMatchers("/actuator/health", "/actuator/health/**").permitAll();

        if (securityProperties.isMetricsPublic()) {
            auth.requestMatchers("/actuator/prometheus", "/actuator/metrics", "/actuator/metrics/**")
                    .permitAll();
        }

        auth.requestMatchers("/actuator/**").denyAll();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
