package com.saii.quizapi.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Configuration de sécurité Spring Security.
 *
 * Stratégie :
 * - Les endpoints candidat (GET/POST /api/sessions/{token}/*) sont publics (accès par token UUID)
 * - Les endpoints admin (POST /api/sessions, /api/quiz/*) nécessitent une clé API
 * - Rate limiting par IP sur tous les endpoints /api/**
 * - Headers de sécurité activés par défaut (X-Content-Type-Options, X-Frame-Options, etc.)
 * - CSRF désactivé (API REST stateless)
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final String apiKey;
    private final int rateLimitPerMinute;

    public SecurityConfig(
            @Value("${saii.security.api-key:dev-api-key-change-me}") final String apiKey,
            @Value("${saii.security.rate-limit-per-minute:60}") final int rateLimitPerMinute) {
        this.apiKey = apiKey;
        this.rateLimitPerMinute = rateLimitPerMinute;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(final HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Endpoints candidat : accès public (le token UUID fait office d'auth)
                        .requestMatchers(HttpMethod.GET, "/api/sessions/{token}").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/sessions/{token}/start").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/sessions/{token}/answers").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/sessions/{token}/complete").permitAll()
                        // SPA Angular et ressources statiques
                        .requestMatchers("/", "/index.html", "/session/**", "/*.js", "/*.css",
                                "/*.ico", "/assets/**").permitAll()
                        // Tous les autres endpoints API nécessitent une clé API
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll()
                )
                .addFilterBefore(new RateLimitFilter(rateLimitPerMinute),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new ApiKeyAuthFilter(apiKey),
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
