package com.linkflow.web.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class WebSecurityConfig {

        /** One year, the minimum for HSTS preload eligibility. */
        private static final long HSTS_MAX_AGE_SECONDS = 31_536_000L;

        /** The app uses none of these device APIs, so they are denied outright. */
        private static final String PERMISSIONS_POLICY =
                        "geolocation=(), microphone=(), camera=(), payment=(), usb=(), magnetometer=()";

        private final SessionAuthFilter sessionAuthFilter;

        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
                http
                                .authorizeHttpRequests(auth -> auth
                                                .requestMatchers(
                                                                "/",
                                                                "/login",
                                                                "/register",
                                                                "/check-email",
                                                                "/verify-email",
                                                                "/resend-verification",
                                                                "/forgot-password",
                                                                "/reset-password",
                                                                "/verify-email-change",
                                                                "/css/**",
                                                                "/js/**",
                                                                "/error",
                                                                // The subpaths matter: the liveness and readiness groups live beneath
                                                                // /actuator/health, and without them a container probe is answered with the
                                                                // login page — a 200 carrying HTML, which reads as healthy to anything
                                                                // checking only the status code.
                                                                "/actuator/health",
                                                                "/actuator/health/**")
                                                .permitAll()
                                                .requestMatchers("/admin/**").hasRole("ADMIN")
                                                .requestMatchers(
                                                                "/dashboard",
                                                                "/dashboard/**",
                                                                "/urls/**",
                                                                "/profile",
                                                                "/profile/**",
                                                                "/tools/**")
                                                .authenticated()
                                                .anyRequest().authenticated())
                                .formLogin(form -> form.disable())
                                .httpBasic(basic -> basic.disable())
                                .logout(logout -> logout.disable())
                                .exceptionHandling(ex -> ex
                                                .authenticationEntryPoint(
                                                                new LoginUrlAuthenticationEntryPoint("/login"))
                                                .accessDeniedHandler((request, response,
                                                                accessDeniedException) -> response.sendRedirect(
                                                                                "/dashboard?error=access_denied")))
                                // CSRF stays on for every state-changing request. The rate-limit
                                // probe is a GET and so was never covered by it anyway.
                                .headers(headers -> headers
                                                .frameOptions(frame -> frame.deny())
                                                .contentTypeOptions(withDefaults())
                                                .referrerPolicy(referrer -> referrer.policy(
                                                                ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                                                // Sent only over HTTPS by Spring Security, so this is
                                                // inert during local HTTP development.
                                                .httpStrictTransportSecurity(hsts -> hsts
                                                                .includeSubDomains(true)
                                                                .maxAgeInSeconds(HSTS_MAX_AGE_SECONDS))
                                                .permissionsPolicyHeader(permissions -> permissions
                                                                .policy(PERMISSIONS_POLICY)))
                                .addFilterBefore(sessionAuthFilter, UsernamePasswordAuthenticationFilter.class);

                return http.build();
        }
}
