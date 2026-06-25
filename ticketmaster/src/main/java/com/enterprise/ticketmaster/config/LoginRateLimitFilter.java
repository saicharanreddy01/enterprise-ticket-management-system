package com.enterprise.ticketmaster.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Order(1)
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration WINDOW    = Duration.ofMinutes(1);

    // One Bucket per IP — each bucket refills MAX_ATTEMPTS tokens every WINDOW
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    // Tracks when each IP last made a request — used for eviction
    private final ConcurrentHashMap<String, Long> lastSeen = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        if (!"POST".equalsIgnoreCase(request.getMethod())
                || !"/api/auth/login".equals(request.getServletPath())) {
            filterChain.doFilter(request, response);
            return;
        }

        String ip     = extractIp(request);
        Bucket bucket = buckets.computeIfAbsent(ip, k -> newBucket());
        lastSeen.put(ip, System.currentTimeMillis());

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"error\": \"Too many login attempts. " +
                            "Maximum " + MAX_ATTEMPTS + " attempts per minute allowed. " +
                            "Please wait before trying again.\"}"
            );
        }
    }

    private Bucket newBucket() {
        Bandwidth limit = Bandwidth.classic(
                MAX_ATTEMPTS,
                Refill.intervally(MAX_ATTEMPTS, WINDOW)
        );
        return Bucket.builder().addLimit(limit).build();
    }

    // Evict IPs not seen in the last 2 minutes — prevents unbounded memory growth
    @Scheduled(fixedDelay = 120_000)
    public void evictStaleBuckets() {
        long cutoff = System.currentTimeMillis() - Duration.ofMinutes(2).toMillis();
        lastSeen.entrySet().removeIf(entry -> {
            if (entry.getValue() < cutoff) {
                buckets.remove(entry.getKey());
                return true;
            }
            return false;
        });
    }

    private String extractIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}