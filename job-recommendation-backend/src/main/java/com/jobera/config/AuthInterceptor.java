package com.jobera.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import com.jobera.entity.User;
import com.jobera.security.AccessPolicy;
import com.jobera.service.ApiKeyService;
import com.jobera.service.AuthenticationService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String AUTHENTICATED_USER = "authenticatedUser";
    public static final String PROCTORING_KEY_HEADER = "X-Proctoring-Key";
    public static final String API_KEY_HEADER = "X-API-Key";

    private static final Set<String> PUBLIC_PATHS = Set.of(
        "/api", "/api/health",
        "/api/auth/login", "/api/auth/register", "/api/auth/logout",
        "/api/auth/forgot-password", "/api/auth/reset-password", "/api/auth/login/2fa",
        "/api/auth/oauth/google", "/api/auth/oauth/google/callback",
        "/api/auth/oauth/linkedin", "/api/auth/oauth/linkedin/callback",
        "/api/webhooks/stripe"
    );
    private static final String PROCTORING_CALLBACK = "/api/aptitude-tests/*/violations";

    // Anyone can browse job listings without an account - only applying, saving or rating a job
    // requires one. Recommendations stay authenticated since they're personalized to whichever
    // resume is on file, which only makes sense for a logged-in user.
    private static final Set<String> PUBLIC_GET_PATTERNS = Set.of(
        "/api/jobs",
        "/api/jobs/search",
        "/api/jobs/{jobId}",
        "/api/jobs/{jobId}/ratings",
        "/api/jobs/{jobId}/rating/stats",
        "/api/companies",
        "/api/companies/{recruiterId}",
        "/api/companies/{recruiterId}/reviews",
        "/api/companies/{recruiterId}/interview-experiences",
        "/api/users/{userId}/badges",
        "/api/salary-insights"
    );

    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final AuthenticationService authenticationService;
    private final AccessPolicy accessPolicy;
    private final ApiKeyService apiKeyService;
    private final String proctoringApiKey;

    public AuthInterceptor(AuthenticationService authenticationService,
                           AccessPolicy accessPolicy,
                           ApiKeyService apiKeyService,
                           @Value("${proctoring.api-key:}") String proctoringApiKey) {
        this.authenticationService = authenticationService;
        this.accessPolicy = accessPolicy;
        this.apiKeyService = apiKeyService;
        this.proctoringApiKey = proctoringApiKey;
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                             @NonNull Object handler) throws IOException {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }

        String path = request.getRequestURI();
        if (PUBLIC_PATHS.contains(path)) {
            return true;
        }
        if (HttpMethod.GET.matches(request.getMethod()) && isPublicGetRoute(path)) {
            return true;
        }
        if (HttpMethod.POST.matches(request.getMethod())
                && pathMatcher.match(PROCTORING_CALLBACK, path)
                && isTrustedProctoringCall(request)) {
            return true;
        }

        String header = request.getHeader("Authorization");
        String apiKeyHeader = request.getHeader(API_KEY_HEADER);

        Optional<User> user;
        if (header != null && header.startsWith("Bearer ")) {
            user = authenticationService.validateToken(header.substring(7));
            if (user.isEmpty()) {
                return reject(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired token");
            }
        } else if (apiKeyHeader != null && !apiKeyHeader.isBlank()) {
            // Machine clients (a recruiter's own integration) authenticate with a generated API
            // key instead of a bearer token - see ApiKeyService. It resolves to the same account
            // a bearer token would, so every AccessPolicy rule below applies identically.
            user = apiKeyService.authenticate(apiKeyHeader);
            if (user.isEmpty()) {
                return reject(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid or revoked API key");
            }
        } else {
            return reject(response, HttpServletResponse.SC_UNAUTHORIZED, "Authentication required");
        }

        if (!accessPolicy.isAllowed(user.get(), request.getMethod(), path, queryParams(request))) {
            return reject(response, HttpServletResponse.SC_FORBIDDEN, "You do not have permission to access this resource");
        }

        request.setAttribute(AUTHENTICATED_USER, user.get());
        return true;
    }

    public static Map<String, String[]> queryParams(HttpServletRequest request) {
        Map<String, String[]> params = new LinkedHashMap<>();
        String query = request.getQueryString();
        if (query == null || query.isBlank()) {
            return params;
        }
        UriComponentsBuilder.fromUriString("?" + query).build().getQueryParams().forEach((name, values) ->
            params.put(UriUtils.decode(name, StandardCharsets.UTF_8),
                values.stream().map(v -> UriUtils.decode(v == null ? "" : v, StandardCharsets.UTF_8)).toArray(String[]::new)));
        return params;
    }

    private boolean isPublicGetRoute(String path) {
        return PUBLIC_GET_PATTERNS.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private boolean isTrustedProctoringCall(HttpServletRequest request) {
        if (proctoringApiKey.isEmpty()) {
            String remote = request.getRemoteAddr();
            return "127.0.0.1".equals(remote) || "0:0:0:0:0:0:0:1".equals(remote) || "::1".equals(remote);
        }
        String provided = request.getHeader(PROCTORING_KEY_HEADER);
        return provided != null && MessageDigest.isEqual(
            provided.getBytes(StandardCharsets.UTF_8), proctoringApiKey.getBytes(StandardCharsets.UTF_8));
    }

    private boolean reject(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"" + message + "\"}");
        return false;
    }
}
