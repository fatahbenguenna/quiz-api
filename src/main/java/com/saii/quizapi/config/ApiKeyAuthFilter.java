package com.saii.quizapi.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filtre Spring Security qui extrait la clé API du header Authorization (Bearer).
 * Si la clé est valide, un contexte d'authentification ROLE_API est posé.
 * Si la clé est absente ou invalide, le filtre passe sans authentifier —
 * c'est la couche d'autorisation (SecurityFilterChain) qui rejettera ou non la requête.
 */
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final String expectedApiKey;

    public ApiKeyAuthFilter(final String expectedApiKey) {
        this.expectedApiKey = expectedApiKey;
    }

    @Override
    protected void doFilterInternal(final HttpServletRequest request,
                                    final HttpServletResponse response,
                                    final FilterChain filterChain) throws ServletException, IOException {
        final var authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            final var apiKey = authHeader.substring(BEARER_PREFIX.length()).trim();

            if (expectedApiKey.equals(apiKey)) {
                final var authentication = new PreAuthenticatedAuthenticationToken(
                        "api-client", null, AuthorityUtils.createAuthorityList("ROLE_API"));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        filterChain.doFilter(request, response);
    }
}
