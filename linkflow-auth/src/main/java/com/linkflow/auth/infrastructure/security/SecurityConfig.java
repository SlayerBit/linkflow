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
import org.springframework.security.web.header.HeaderWriter;
import org.springframework.security.web.header.writers.ContentSecurityPolicyHeaderWriter;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfigurationSource;
import jakarta.servlet.Filter;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String[] SWAGGER_PATHS = {
            "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/v3/api-docs.yaml"
    };

    /** One year, the minimum for HSTS preload eligibility. */
    private static final long HSTS_MAX_AGE_SECONDS = 31_536_000L;

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
                            "/api/v1/auth/resend-verification",
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
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(referrer -> referrer.policy(
                                ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        // Emitted only over HTTPS, so local HTTP development is unaffected.
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(HSTS_MAX_AGE_SECONDS))
                        .addHeaderWriter(apiContentSecurityPolicyWriter()))
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

    /**
     * Locks down every API response with {@code default-src 'none'}: JSON should never pull in a
     * subresource, so if a response is ever coerced into being rendered as a document, nothing in
     * it can execute.
     * <p>
     * Swagger UI is exempt because it is a real HTML page that loads its own assets, and this
     * policy would leave it blank.
     */
    private HeaderWriter apiContentSecurityPolicyWriter() {
        RequestMatcher swagger = new OrRequestMatcher(
                Arrays.stream(SWAGGER_PATHS)
                        .map(path -> (RequestMatcher) new AntPathRequestMatcher(path))
                        .toList());

        return new DelegatingRequestMatcherHeaderWriter(
                new NegatedRequestMatcher(swagger),
                new ContentSecurityPolicyHeaderWriter("default-src 'none'; frame-ancestors 'none'"));
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
