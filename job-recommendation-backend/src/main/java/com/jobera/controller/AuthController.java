package com.jobera.controller;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jobera.entity.User;
import com.jobera.security.LoginAttemptService;
import com.jobera.security.PasswordResetRateLimiter;
import com.jobera.security.RegistrationRateLimiter;
import com.jobera.security.TwoFactorAttemptService;
import com.jobera.service.AuthenticationService;
import com.jobera.service.NotificationService;
import com.jobera.service.PasswordResetService;
import com.jobera.service.TwoFactorService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    // Constants for repeated strings
    private static final String EMAIL = "email";
    private static final String MESSAGE = "message";
    private static final String ERROR = "error";
    private static final String USER = "user";
    private static final String TOKEN = "token";
    private static final int MIN_PASSWORD_LENGTH = 8;

    private final AuthenticationService authenticationService;
    private final LoginAttemptService loginAttemptService;
    private final PasswordResetService passwordResetService;
    private final PasswordResetRateLimiter passwordResetRateLimiter;
    private final RegistrationRateLimiter registrationRateLimiter;
    private final NotificationService notificationService;
    private final TwoFactorService twoFactorService;
    private final TwoFactorAttemptService twoFactorAttemptService;
    private final String frontendUrl;

    public AuthController(AuthenticationService authenticationService,
                          LoginAttemptService loginAttemptService,
                          PasswordResetService passwordResetService,
                          PasswordResetRateLimiter passwordResetRateLimiter,
                          RegistrationRateLimiter registrationRateLimiter,
                          NotificationService notificationService,
                          TwoFactorService twoFactorService,
                          TwoFactorAttemptService twoFactorAttemptService,
                          @Value("${app.frontend-url:http://localhost:3000}") String frontendUrl) {
        this.authenticationService = authenticationService;
        this.loginAttemptService = loginAttemptService;
        this.passwordResetService = passwordResetService;
        this.passwordResetRateLimiter = passwordResetRateLimiter;
        this.registrationRateLimiter = registrationRateLimiter;
        this.notificationService = notificationService;
        this.twoFactorService = twoFactorService;
        this.twoFactorAttemptService = twoFactorAttemptService;
        this.frontendUrl = frontendUrl;
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody Map<String, String> request,
                                                        HttpServletRequest httpRequest) {
        String address = httpRequest.getRemoteAddr();
        if (registrationRateLimiter.isBlocked(address)) {
            long minutes = registrationRateLimiter.retryAfterMinutes(address);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(minutes * 60))
                .body(Map.of(ERROR, "Too many registration attempts. Try again in " + minutes + " minute(s)."));
        }
        registrationRateLimiter.recordAttempt(address);

        try {
            String email = request.get(EMAIL);
            String password = request.get("password");
            String fullName = request.get("fullName");
            String phone = request.get("phone");
            String referralCode = request.get("referralCode");

            User user = authenticationService.registerUser(email, password, fullName, phone, referralCode);
            String authToken = authenticationService.generateAuthToken(user,
                httpRequest.getHeader("User-Agent"), httpRequest.getRemoteAddr());
            notificationService.sendWelcome(user.getEmail(), user.getFullName());

            Map<String, Object> response = new HashMap<>();
            response.put(MESSAGE, "User registered successfully");
            response.put(USER, createUserResponse(user));
            response.put(TOKEN, authToken);
            
            return ResponseEntity.ok(response);
            
        } catch (AuthenticationService.AuthenticationException e) {
            return ResponseEntity.badRequest()
                .body(Map.of(ERROR, e.getMessage()));
        }
    }
    
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> request,
                                                     HttpServletRequest httpRequest) {
        String email = request.get(EMAIL);
        String password = request.get("password");
        String address = httpRequest.getRemoteAddr();

        if (loginAttemptService.isBlocked(email, address)) {
            long minutes = loginAttemptService.retryAfterMinutes(email, address);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(minutes * 60))
                .body(Map.of(ERROR, "Too many failed login attempts. Try again in " + minutes + " minute(s)."));
        }

        try {
            User user = authenticationService.loginUser(email, password);
            // The password is correct at this point, so this counts as a successful attempt for
            // rate-limiting purposes regardless of whether a second factor is still needed - 2FA
            // protects a different step, and shouldn't cost the user their password-attempt budget.
            loginAttemptService.recordSuccess(email);

            if (Boolean.TRUE.equals(user.getTwoFactorEnabled())) {
                return ResponseEntity.ok(Map.of(
                    "requiresTwoFactor", true,
                    "userId", user.getId(),
                    MESSAGE, "Enter your authenticator code to finish signing in"
                ));
            }

            String authToken = authenticationService.generateAuthToken(user,
                httpRequest.getHeader("User-Agent"), address);
            Map<String, Object> response = new HashMap<>();
            response.put(MESSAGE, "Login successful");
            response.put(USER, createUserResponse(user));
            response.put(TOKEN, authToken);

            return ResponseEntity.ok(response);

        } catch (AuthenticationService.AuthenticationException e) {
            loginAttemptService.recordFailure(email, address);
            return ResponseEntity.badRequest()
                .body(Map.of(ERROR, e.getMessage()));
        }
    }

    @PostMapping("/login/2fa")
    public ResponseEntity<Map<String, Object>> loginTwoFactor(@RequestBody Map<String, Object> request,
                                                              HttpServletRequest httpRequest) {
        Long userId;
        try {
            userId = Long.valueOf(String.valueOf(request.get("userId")));
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of(ERROR, "Invalid user"));
        }
        String code = String.valueOf(request.get("code"));

        if (twoFactorAttemptService.isBlocked(userId)) {
            long minutes = twoFactorAttemptService.retryAfterMinutes(userId);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(minutes * 60))
                .body(Map.of(ERROR, "Too many failed attempts. Try again in " + minutes + " minute(s)."));
        }

        Optional<User> userOptional = authenticationService.findById(userId);
        if (userOptional.isEmpty() || !Boolean.TRUE.equals(userOptional.get().getTwoFactorEnabled())) {
            return ResponseEntity.badRequest().body(Map.of(ERROR, "Invalid request"));
        }
        User user = userOptional.get();

        if (!twoFactorService.verifyLoginCode(user, code)) {
            twoFactorAttemptService.recordFailure(userId);
            return ResponseEntity.badRequest().body(Map.of(ERROR, "Incorrect code"));
        }
        twoFactorAttemptService.recordSuccess(userId);

        String authToken = authenticationService.generateAuthToken(user,
            httpRequest.getHeader("User-Agent"), httpRequest.getRemoteAddr());
        Map<String, Object> response = new HashMap<>();
        response.put(MESSAGE, "Login successful");
        response.put(USER, createUserResponse(user));
        response.put(TOKEN, authToken);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            authenticationService.logout(token);
        }
        
        return ResponseEntity.ok(Map.of(MESSAGE, "Logout successful"));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@RequestBody Map<String, String> request) {
        String email = request.get(EMAIL);
        // Same response whether the account exists, is inactive, or the request was rate-limited -
        // this endpoint never reveals which emails have accounts.
        if (email != null && !email.isBlank() && !passwordResetRateLimiter.isBlocked(email)) {
            passwordResetRateLimiter.recordRequest(email);
            passwordResetService.requestReset(email, frontendUrl);
        }
        return ResponseEntity.ok(Map.of(MESSAGE, "If an account exists for that email, a reset link has been sent."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@RequestBody Map<String, String> request) {
        String token = request.get(TOKEN);
        String newPassword = request.get("password");

        if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
            return ResponseEntity.badRequest()
                .body(Map.of(ERROR, "Password must be at least " + MIN_PASSWORD_LENGTH + " characters"));
        }

        try {
            passwordResetService.resetPassword(token, newPassword);
            return ResponseEntity.ok(Map.of(MESSAGE, "Password updated. You can now log in with your new password."));
        } catch (PasswordResetService.InvalidResetTokenException e) {
            return ResponseEntity.badRequest().body(Map.of(ERROR, e.getMessage()));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getCurrentUser(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put(ERROR, "Authentication required");
            return ResponseEntity.status(401).body(errorResponse);
        }
        
        String token = authHeader.substring(7);
        Optional<User> userOptional = authenticationService.validateToken(token);
        
        if (userOptional.isPresent()) {
            Map<String, Object> successResponse = new HashMap<>();
            successResponse.put(USER, createUserResponse(userOptional.get()));
            return ResponseEntity.ok(successResponse);
        } else {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put(ERROR, "Invalid or expired token");
            return ResponseEntity.status(401).body(errorResponse);
        }
    }
    
    private Map<String, Object> createUserResponse(User user) {
        Map<String, Object> userResponse = new HashMap<>();
        userResponse.put("id", user.getId());
        userResponse.put(EMAIL, user.getEmail());
        userResponse.put("fullName", user.getFullName());
        userResponse.put("phone", user.getPhone());
        userResponse.put("role", user.getRole());
        userResponse.put("lastLogin", user.getLastLogin());
        userResponse.put("createdAt", user.getCreatedAt());
        userResponse.put("referralCode", user.getReferralCode());

        return userResponse;
    }
}