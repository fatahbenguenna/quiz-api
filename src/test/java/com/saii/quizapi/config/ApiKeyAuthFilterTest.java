package com.saii.quizapi.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ApiKeyAuthFilterTest {

    private static final String VALID_KEY = "test-api-key-secret";

    @Mock
    private FilterChain filterChain;

    private ApiKeyAuthFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        filter = new ApiKeyAuthFilter(VALID_KEY);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @Test
    void should_authenticate_when_valid_bearer_key() throws ServletException, IOException {
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_KEY);

        filter.doFilterInternal(request, response, filterChain);

        final var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isEqualTo("api-client");
        assertThat(auth.getAuthorities()).anyMatch(a -> a.getAuthority().equals("ROLE_API"));
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void should_not_authenticate_when_invalid_key() throws ServletException, IOException {
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer wrong-key");

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void should_not_authenticate_when_no_auth_header() throws ServletException, IOException {
        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void should_not_authenticate_when_non_bearer_scheme() throws ServletException, IOException {
        request.addHeader(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz");

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void should_trim_whitespace_in_api_key() throws ServletException, IOException {
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer   " + VALID_KEY + "  ");

        filter.doFilterInternal(request, response, filterChain);

        final var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isEqualTo("api-client");
    }
}
