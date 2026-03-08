package com.saii.quizapi.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitFilterTest {

    @Test
    void should_allow_requests_within_limit() throws Exception {
        final var filter = new RateLimitFilter(5);
        final var request = new MockHttpServletRequest("GET", "/api/quiz/1");
        final var response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader("X-Rate-Limit-Remaining")).isEqualTo("4");
    }

    @Test
    void should_reject_when_limit_exceeded() throws Exception {
        final var filter = new RateLimitFilter(3);

        for (int i = 0; i < 3; i++) {
            final var req = new MockHttpServletRequest("GET", "/api/quiz/1");
            filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());
        }

        // 4e requête doit être rejetée
        final var request = new MockHttpServletRequest("GET", "/api/quiz/1");
        final var response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isNotNull();
        assertThat(response.getContentAsString()).contains("Trop de requêtes");
    }

    @Test
    void should_not_filter_non_api_requests() throws Exception {
        final var filter = new RateLimitFilter(1);
        final var request = new MockHttpServletRequest("GET", "/index.html");
        final var response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
        // Pas de header rate limit sur les ressources statiques
        assertThat(response.getHeader("X-Rate-Limit-Remaining")).isNull();
    }

    @Test
    void should_isolate_limits_per_ip() throws Exception {
        final var filter = new RateLimitFilter(1);

        // IP 1 — consomme son unique token
        final var req1 = new MockHttpServletRequest("GET", "/api/quiz/1");
        req1.setRemoteAddr("10.0.0.1");
        filter.doFilter(req1, new MockHttpServletResponse(), new MockFilterChain());

        // IP 2 — a son propre bucket, doit passer
        final var req2 = new MockHttpServletRequest("GET", "/api/quiz/1");
        req2.setRemoteAddr("10.0.0.2");
        final var response2 = new MockHttpServletResponse();
        filter.doFilter(req2, response2, new MockFilterChain());

        assertThat(response2.getStatus()).isEqualTo(200);
    }
}
