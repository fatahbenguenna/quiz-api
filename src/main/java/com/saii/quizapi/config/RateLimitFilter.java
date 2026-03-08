package com.saii.quizapi.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Filtre de rate limiting par IP sur les endpoints /api/**.
 * Utilise l'algorithme Token Bucket (Bucket4j) : chaque IP dispose
 * d'un bucket rechargé à un rythme configurable.
 */
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final int requestsPerMinute;

    public RateLimitFilter(final int requestsPerMinute) {
        this.requestsPerMinute = requestsPerMinute;
    }

    @Override
    protected void doFilterInternal(final HttpServletRequest request,
                                    final HttpServletResponse response,
                                    final FilterChain filterChain) throws ServletException, IOException {
        final var clientIp = resolveClientIp(request);
        final var bucket = buckets.computeIfAbsent(clientIp, k -> createBucket());
        final var probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            response.setHeader("X-Rate-Limit-Remaining",
                    String.valueOf(probe.getRemainingTokens()));
            filterChain.doFilter(request, response);
        } else {
            log.warn("Rate limit dépassé pour IP={}", clientIp);
            final var retryAfterSeconds = probe.getNanosToWaitForRefill() / 1_000_000_000 + 1;
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
            response.getWriter().write("""
                    {"type":"about:blank","title":"Trop de requêtes","status":429,\
                    "detail":"Limite de débit dépassée, réessayez dans %d secondes"}"""
                    .formatted(retryAfterSeconds));
        }
    }

    @Override
    protected boolean shouldNotFilter(final HttpServletRequest request) {
        // Ne rate-limiter que les endpoints API
        return !request.getRequestURI().startsWith("/api/");
    }

    private Bucket createBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.simple(requestsPerMinute, Duration.ofMinutes(1)))
                .build();
    }

    private String resolveClientIp(final HttpServletRequest request) {
        final var forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // Prendre la première IP (client réel derrière un proxy/load balancer)
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
