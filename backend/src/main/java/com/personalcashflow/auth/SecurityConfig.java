package com.personalcashflow.auth;

import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationEntryPointFailureHandler;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;

/**
 * Session-cookie login for the Angular SPA, served on the same origin (proxy in dev, reverse proxy in prod).
 * Everything answers with plain status codes instead of redirects; the SPA handles navigation.
 */
@Configuration
class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            @Value("${app.remember-me.key}") String rememberMeKey) throws Exception {
        HttpStatusEntryPoint unauthorized = new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED);
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/api/users/**").hasRole(UserRole.ADMIN.name())
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginProcessingUrl("/api/auth/login")
                        .successHandler((request, response, authentication) -> response.setStatus(HttpStatus.OK.value()))
                        .failureHandler(new AuthenticationEntryPointFailureHandler(unauthorized))
                        .permitAll())
                .rememberMe(remember -> remember
                        // No key configured: a random one is safe, it only logs remembered users out on restart.
                        .key(rememberMeKey.isBlank() ? UUID.randomUUID().toString() : rememberMeKey)
                        .tokenValiditySeconds((int) Duration.ofDays(30).toSeconds()))
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(unauthorized))
                .csrf(csrf -> csrf.spa());
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
