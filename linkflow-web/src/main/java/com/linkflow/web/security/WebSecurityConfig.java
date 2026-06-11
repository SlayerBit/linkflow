package com.linkflow.web.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class WebSecurityConfig {

        private final SessionAuthFilter sessionAuthFilter;

        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
                http
                                .authorizeHttpRequests(auth -> auth
                                                .requestMatchers(
                                                                "/",
                                                                "/login",
                                                                "/register",
                                                                "/css/**",
                                                                "/js/**",
                                                                "/error",
                                                                "/actuator/health")
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
                                .csrf(csrf -> csrf.ignoringRequestMatchers("/tools/rate-limit/probe"))
                                .addFilterBefore(sessionAuthFilter, UsernamePasswordAuthenticationFilter.class);

                return http.build();
        }
}
