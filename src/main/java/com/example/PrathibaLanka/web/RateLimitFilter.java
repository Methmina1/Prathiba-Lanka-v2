package com.example.PrathibaLanka.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A token bucket per client address, for the endpoints a stranger is allowed to write to.
 *
 * <p>Two of them are worth protecting: {@code POST /api/contact} writes a row and sends mail (a loop
 * can flood the enquiry table and burn the sending account's daily quota, which then takes email down
 * for real customers), and {@code /api/auth/login} is a password guess away from an account with no
 * limit at all.
 *
 * <p>Counters live in this instance's memory. That is the honest trade for a single replica - a second
 * replica means two independent budgets, and a restart forgets them. If this ever runs scaled out, the
 * counters belong in Redis or at the platform's edge.
 *
 * <p>Runs at the front of the servlet chain, ahead of Spring Security, so that a flood never reaches
 * authentication at all.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    /** Buckets idle for this long are dropped, so the map cannot grow without bound. */
    private static final Duration IDLE_TTL = Duration.ofMinutes(30);
    private static final int SWEEP_THRESHOLD = 10_000;

    private final boolean enabled;
    private final int requestsPerMinute;
    private final int burst;
    private final List<Entry> paths;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimitFilter(
            @Value("${app.rate-limit.enabled:true}") boolean enabled,
            @Value("${app.rate-limit.requests-per-minute:20}") int requestsPerMinute,
            @Value("${app.rate-limit.burst:5}") int burst,
            @Value("${app.rate-limit.paths:/api/contact,/api/auth/login,/api/auth/register}") String paths) {
        this.enabled = enabled;
        this.requestsPerMinute = Math.max(1, requestsPerMinute);
        this.burst = Math.max(0, burst);
        this.paths = Arrays.stream(paths.split(",")).map(String::trim).filter(p -> !p.isEmpty())
                .map(Entry::parse).toList();
        if (enabled) {
            log.info("Rate limiting {} at {}/minute (burst {})", this.paths, this.requestsPerMinute, this.burst);
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !enabled || matchedPath(request) == null;
    }

    /** The configured endpoint this request belongs to, or null if it is not rate limited. */
    private String matchedPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null) {
            return null;
        }
        return paths.stream()
                .filter(entry -> entry.matches(request.getMethod(), path))
                .map(Entry::label)
                .findFirst()
                .orElse(null);
    }

    /**
     * One configured rate limit: a path prefix, optionally restricted to one method.
     *
     * <p>The method matters for the customer's own enquiry page. {@code /api/enquiries} is both a read
     * and a write: writing has to be limited, because it costs a mail and lands in somebody's inbox,
     * while reading is how a customer looks at their own enquiry and throttling it would answer a
     * double-click with "too many requests". A bare prefix - the older form - limits every method, which
     * is still right for {@code /api/contact} and the auth endpoints.
     */
    private record Entry(String method, String prefix) {

        static Entry parse(String raw) {
            int space = raw.indexOf(' ');
            if (space > 0) {
                return new Entry(raw.substring(0, space).trim().toUpperCase(), raw.substring(space + 1).trim());
            }
            return new Entry(null, raw);
        }

        boolean matches(String requestMethod, String path) {
            return (method == null || method.equalsIgnoreCase(requestMethod)) && path.startsWith(prefix);
        }

        String label() {
            return method == null ? prefix : method + ' ' + prefix;
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String endpoint = matchedPath(request);
        String client = address(request);
        // One bucket per client *and endpoint*: sharing a bucket between them meant a run of failed
        // sign-ins (or somebody else on the same address) could block a genuine enquiry.
        Bucket bucket = bucketFor(client + ' ' + endpoint);

        if (!bucket.tryConsume()) {
            long retryAfter = bucket.secondsUntilNextToken();
            log.warn("Rate limit hit for {} on {} {}", client, request.getMethod(), request.getRequestURI());
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Retry-After", String.valueOf(retryAfter));
            response.getWriter().write(
                    "{\"status\":429,\"error\":\"Too Many Requests\",\"message\":\"Too many requests. "
                            + "Please wait " + retryAfter + " second(s) and try again.\"}");
            return;
        }

        chain.doFilter(request, response);
    }

    /**
     * The client address.
     *
     * Read from X-Forwarded-For first, because this filter runs ahead of the filter that rewrites the
     * request for the platform's proxy. The first entry is the client a proxy chain reports; a caller
     * who sends the header themselves can therefore choose their bucket, which is the known weakness
     * of limiting by a forwarded header - the alternative, the proxy's own address, would put every
     * visitor in one bucket and make the limit useless.
     */
    private String address(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String first = forwarded.split(",")[0].trim();
            if (!first.isEmpty()) {
                return first;
            }
        }
        String remote = request.getRemoteAddr();
        return remote == null || remote.isBlank() ? "unknown" : remote;
    }

    private Bucket bucketFor(String key) {
        if (buckets.size() > SWEEP_THRESHOLD) {
            Instant cutoff = Instant.now().minus(IDLE_TTL);
            buckets.entrySet().removeIf(entry -> entry.getValue().lastSeen.isBefore(cutoff));
        }
        return buckets.computeIfAbsent(key, value -> new Bucket(requestsPerMinute, burst));
    }

    /** {@code requestsPerMinute} refills one token every 60/n seconds; {@code burst} is the ceiling. */
    private static final class Bucket {
        private final double tokensPerSecond;
        private final int capacity;
        private double tokens;
        private Instant lastRefill;
        private volatile Instant lastSeen;

        Bucket(int requestsPerMinute, int burst) {
            this.tokensPerSecond = requestsPerMinute / 60.0;
            this.capacity = Math.max(1, requestsPerMinute + burst);
            this.tokens = capacity;
            this.lastRefill = Instant.now();
            this.lastSeen = this.lastRefill;
        }

        synchronized boolean tryConsume() {
            Instant now = Instant.now();
            lastSeen = now;
            tokens = Math.min(capacity, tokens + Duration.between(lastRefill, now).toNanos() / 1_000_000_000.0 * tokensPerSecond);
            lastRefill = now;
            if (tokens < 1) {
                return false;
            }
            tokens -= 1;
            return true;
        }

        synchronized long secondsUntilNextToken() {
            double missing = 1 - tokens;
            if (missing <= 0) {
                return 0;
            }
            return Math.max(1, (long) Math.ceil(missing / tokensPerSecond));
        }
    }
}
