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
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Filtre de rate limiting par IP sur les endpoints /api/**.
 * Utilise l'algorithme Token Bucket (Bucket4j) : chaque IP dispose
 * d'un bucket rechargé à un rythme configurable.
 * <p>
 * Les entrées inactives sont purgées automatiquement toutes les 10 minutes
 * pour éviter une fuite mémoire sur les maps de buckets.
 */
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Duration BUCKET_EXPIRATION = Duration.ofMinutes(10);

    private final Map<String, TimestampedBucket> buckets = new ConcurrentHashMap<>();
    private final int requestsPerMinute;
    private volatile Instant lastPurge = Instant.now();

    public RateLimitFilter(final int requestsPerMinute) {
        this.requestsPerMinute = requestsPerMinute;
    }

    @Override
    protected void doFilterInternal(final HttpServletRequest request,
                                    final HttpServletResponse response,
                                    final FilterChain filterChain) throws ServletException, IOException {
        purgeExpiredBuckets();

        final var clientIp = resolveClientIp(request);
        final var timestamped = buckets.compute(clientIp, (key, existing) -> {
            if (existing == null) {
                return new TimestampedBucket(createBucket(), Instant.now());
            }
            return existing.touch();
        });
        final var probe = timestamped.bucket().tryConsumeAndReturnRemaining(1);

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
        return !request.getRequestURI().startsWith("/api/");
    }

    private void purgeExpiredBuckets() {
        final var now = Instant.now();
        if (Duration.between(lastPurge, now).compareTo(BUCKET_EXPIRATION) < 0) {
            return;
        }
        lastPurge = now;
        final var threshold = now.minus(BUCKET_EXPIRATION);
        buckets.entrySet().removeIf(entry -> entry.getValue().lastAccess().isBefore(threshold));
    }

    private Bucket createBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.simple(requestsPerMinute, Duration.ofMinutes(1)))
                .build();
    }

    private String resolveClientIp(final HttpServletRequest request) {
        final var forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * Bucket avec timestamp du dernier accès, pour permettre la purge des entrées inactives.
     */
    private record TimestampedBucket(Bucket bucket, Instant lastAccess) {
        TimestampedBucket touch() {
            return new TimestampedBucket(this.bucket, Instant.now());
        }
    }
}
