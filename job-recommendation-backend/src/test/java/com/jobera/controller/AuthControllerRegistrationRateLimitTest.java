package com.jobera.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import com.jobera.security.LoginAttemptService;
import com.jobera.security.PasswordResetRateLimiter;
import com.jobera.security.RegistrationRateLimiter;
import com.jobera.security.TwoFactorAttemptService;
import com.jobera.service.AuthenticationService;
import com.jobera.service.NotificationService;
import com.jobera.service.PasswordResetService;
import com.jobera.service.TwoFactorService;

/** Only /register's new rate-limiting gate - the rest of AuthController has its own scoped test
 *  files (AuthControllerPasswordResetTest, AuthControllerTwoFactorLoginTest). */
@ExtendWith(MockitoExtension.class)
class AuthControllerRegistrationRateLimitTest {

    @Mock AuthenticationService authenticationService;
    @Mock LoginAttemptService loginAttemptService;
    @Mock PasswordResetService passwordResetService;
    @Mock PasswordResetRateLimiter passwordResetRateLimiter;
    @Mock RegistrationRateLimiter registrationRateLimiter;
    @Mock NotificationService notificationService;
    @Mock TwoFactorService twoFactorService;
    @Mock TwoFactorAttemptService twoFactorAttemptService;

    AuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthController(authenticationService, loginAttemptService, passwordResetService,
            passwordResetRateLimiter, registrationRateLimiter, notificationService, twoFactorService,
            twoFactorAttemptService, "http://localhost:3000");
    }

    private MockHttpServletRequest requestFrom(String address) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(address);
        return request;
    }

    @Test
    void registrationIsBlockedAfterTooManyAttemptsFromOneAddressWithoutTouchingTheService() {
        when(registrationRateLimiter.isBlocked("9.9.9.9")).thenReturn(true);
        when(registrationRateLimiter.retryAfterMinutes("9.9.9.9")).thenReturn(42L);

        ResponseEntity<Map<String, Object>> response = controller.register(
            Map.of("email", "a@b.com", "password", "pw123456", "fullName", "A"), requestFrom("9.9.9.9"));

        assertEquals(429, response.getStatusCode().value());
        assertEquals("2520", response.getHeaders().getFirst("Retry-After"));
        verify(authenticationService, never()).registerUser(anyString(), anyString(), anyString(), any(), any());
        verify(registrationRateLimiter, never()).recordAttempt(anyString());
    }

    @Test
    void anAllowedRegistrationAttemptIsRecordedEvenIfItThenFails() {
        when(registrationRateLimiter.isBlocked("1.2.3.4")).thenReturn(false);
        when(authenticationService.registerUser(anyString(), anyString(), anyString(), any(), any()))
            .thenThrow(new AuthenticationService.AuthenticationException("Email already registered"));

        ResponseEntity<Map<String, Object>> response = controller.register(
            Map.of("email", "a@b.com", "password", "pw123456", "fullName", "A"), requestFrom("1.2.3.4"));

        assertEquals(400, response.getStatusCode().value());
        verify(registrationRateLimiter).recordAttempt("1.2.3.4");
    }
}
