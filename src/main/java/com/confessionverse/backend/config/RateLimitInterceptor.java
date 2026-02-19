package com.confessionverse.backend.config;

import com.confessionverse.backend.model.User;
import com.confessionverse.backend.repository.UserRepository;
import com.confessionverse.backend.service.SubscriptionEntitlementService;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bucket4j;
import io.github.bucket4j.Refill;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.io.IOException;
import java.security.Principal;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
@ConditionalOnProperty(name = "rate-limit.enabled", havingValue = "true", matchIfMissing = true)
public class RateLimitInterceptor implements HandlerInterceptor {

    private final UserRepository userRepository;
    private final SubscriptionEntitlementService subscriptionEntitlementService;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimitInterceptor(UserRepository userRepository,
                                SubscriptionEntitlementService subscriptionEntitlementService) {
        this.userRepository = userRepository;
        this.subscriptionEntitlementService = subscriptionEntitlementService;
    }

    private Bucket createNewBucket(int limit, Duration duration) {
        return Bucket4j.builder()
                .addLimit(Bandwidth.classic(limit, Refill.intervally(limit, duration)))
                .build();
    }



    private Bucket resolveBucket(String key, String path, String method, boolean authenticated) {
        // Different limits per endpoint and method
        if (path.contains("/api/billing/webhook")) {
            return buckets.computeIfAbsent("stripe_webhook", k -> createNewBucket(600, Duration.ofMinutes(1)));
        } else if (path.contains("/api/auth/forgot-password")) {
            return buckets.computeIfAbsent(key + "_forgot_password", k -> createNewBucket(5, Duration.ofMinutes(10)));
        } else if (path.contains("/api/confessions")) {
            if ("GET".equalsIgnoreCase(method)) {
                int limit = authenticated ? 120 : 60;
                return buckets.computeIfAbsent(key + "_confess_get", k -> createNewBucket(limit, Duration.ofMinutes(1)));
            } else {
                int limit = authenticated ? 20 : 5;
                return buckets.computeIfAbsent(key + "_confess_write", k -> createNewBucket(limit, Duration.ofMinutes(1)));
            }
        } else if (path.contains("/api/ai/reply")) {
            return buckets.computeIfAbsent(key + "_ai", k -> createNewBucket(2, Duration.ofSeconds(10)));
        } else {
            int limit = authenticated ? 120 : 60;
            return buckets.computeIfAbsent(key + "_default", k -> createNewBucket(limit, Duration.ofMinutes(1)));
        }
    }

    private String getKey(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            return "USER_" + auth.getName();
        } else {
            return "IP_" + request.getRemoteAddr();
        }
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.isAuthenticated()
                && auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"))) {
            System.out.println("Admin detected, skipping rate limit for: " + auth.getName());
            return true;  // skip rate limit for admin
        }
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            Optional<User> user = userRepository.findByEmail(auth.getName());
            if (user.isPresent() && subscriptionEntitlementService.isPro(user.get().getId())) {
                return true; // PRO users have no rate limit
            }
        }

        String key = getKey(request);
        String path = request.getRequestURI();
        String method = request.getMethod();
        boolean authenticated = auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal());

        Bucket bucket = resolveBucket(key, path, method, authenticated);

        System.out.println("KEY = " + key + " | PATH = " + path + " | Remaining tokens = " + bucket.getAvailableTokens());

        if (bucket.tryConsume(1)) {
            response.setHeader("X-RateLimit-Remaining", String.valueOf(bucket.getAvailableTokens()));
            return true;
        } else {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.setHeader("Retry-After", "10");
            response.setHeader("X-RateLimit-Remaining", "0");

            if (path.contains("/api/ai/reply")) {
                response.getWriter().write(
                        "{\n" +
                        "  \"status\": 429,\n" +
                        "  \"code\": \"FREE_LIMIT_REACHED\",\n" +
                        "  \"error\": \"Free plan limit reached. Upgrade to PRO to continue.\",\n" +
                        "  \"timestamp\": \"" + java.time.Instant.now() + "\"\n" +
                        "}"
                );
            } else {
                response.getWriter().write(
                        "{\n" +
                        "  \"status\": 429,\n" +
                        "  \"error\": \"Too Many Requests\",\n" +
                        "  \"message\": \"Rate limit exceeded. Please try again later.\",\n" +
                        "  \"timestamp\": \"" + java.time.Instant.now() + "\"\n" +
                        "}"
                );
            }
            return false;
        }
    }

}

