package com.ignaciodeserti.kanban.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Throttles the unauthenticated auth endpoints per client IP, so a script can't brute-force logins
 * or spam account creation / password-reset emails. Thrown as an exception (not written directly)
 * so it goes through GlobalExceptionHandler and gets a consistent JSON body.
 */
@Component
public class AuthRateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiter rateLimiter;
    private final Map<String, Limit> limits;

    public AuthRateLimitInterceptor(
            RateLimiter rateLimiter,
            @Value("${app.rate-limit.login.max-attempts:10}") int loginMax,
            @Value("${app.rate-limit.login.window-seconds:60}") long loginWindowSeconds,
            @Value("${app.rate-limit.register.max-attempts:5}") int registerMax,
            @Value("${app.rate-limit.register.window-seconds:600}") long registerWindowSeconds,
            @Value("${app.rate-limit.password-reset.max-attempts:5}") int passwordResetMax,
            @Value("${app.rate-limit.password-reset.window-seconds:600}")
                    long passwordResetWindowSeconds) {
        this.rateLimiter = rateLimiter;
        this.limits =
                Map.of(
                        "/api/auth/login",
                                new Limit(loginMax, Duration.ofSeconds(loginWindowSeconds)),
                        "/api/auth/register",
                                new Limit(registerMax, Duration.ofSeconds(registerWindowSeconds)),
                        "/api/auth/forgot-password",
                                new Limit(
                                        passwordResetMax,
                                        Duration.ofSeconds(passwordResetWindowSeconds)),
                        "/api/auth/resend-verification",
                                new Limit(
                                        passwordResetMax,
                                        Duration.ofSeconds(passwordResetWindowSeconds)));
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler) {
        Limit limit = limits.get(request.getRequestURI());
        if (limit == null) {
            return true;
        }

        String key = clientIp(request) + ":" + request.getRequestURI();
        if (!rateLimiter.tryAcquire(key, limit.maxAttempts(), limit.window())) {
            throw new RateLimitExceededException("Too many attempts — please wait and try again");
        }
        return true;
    }

    /** Honors X-Forwarded-For when running behind a reverse proxy (nginx, a load balancer). */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private record Limit(int maxAttempts, Duration window) {}
}
