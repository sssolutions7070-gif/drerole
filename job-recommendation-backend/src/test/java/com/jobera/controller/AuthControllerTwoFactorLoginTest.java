package com.jobera.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import com.jobera.entity.User;
import com.jobera.security.LoginAttemptService;
import com.jobera.security.PasswordResetRateLimiter;
import com.jobera.security.RegistrationRateLimiter;
import com.jobera.security.TwoFactorAttemptService;
import com.jobera.service.AuthenticationService;
import com.jobera.service.NotificationService;
import com.jobera.service.PasswordResetService;
import com.jobera.service.TwoFactorService;

@ExtendWith(MockitoExtension.class)
class AuthControllerTwoFactorLoginTest {

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

    private User userWithTwoFactor(boolean enabled) {
        User user = new User("a@b.com", "hash", "Asha");
        user.setId(5L);
        user.setTwoFactorEnabled(enabled);
        return user;
    }

    @Test
    void loginWithoutTwoFactorIssuesATokenImmediately() {
        User user = userWithTwoFactor(false);
        when(authenticationService.loginUser("a@b.com", "pw")).thenReturn(user);
        when(authenticationService.generateAuthToken(eq(user), any(), any())).thenReturn("real-token");

        ResponseEntity<Map<String, Object>> response =
            controller.login(Map.of("email", "a@b.com", "password", "pw"), new MockHttpServletRequest());

        assertEquals("real-token", response.getBody().get("token"));
        assertNull(response.getBody().get("requiresTwoFactor"));
    }

    @Test
    void loginWithTwoFactorEnabledWithholdsTheTokenAndAsksForACode() {
        User user = userWithTwoFactor(true);
        when(authenticationService.loginUser("a@b.com", "pw")).thenReturn(user);

        ResponseEntity<Map<String, Object>> response =
            controller.login(Map.of("email", "a@b.com", "password", "pw"), new MockHttpServletRequest());

        assertEquals(Boolean.TRUE, response.getBody().get("requiresTwoFactor"));
        assertEquals(5L, response.getBody().get("userId"));
        assertFalse(response.getBody().containsKey("token"));
        verify(authenticationService, never()).generateAuthToken(any());
    }

    @Test
    void twoFactorLoginStepIssuesATokenOnACorrectCode() {
        User user = userWithTwoFactor(true);
        when(twoFactorAttemptService.isBlocked(5L)).thenReturn(false);
        when(authenticationService.findById(5L)).thenReturn(Optional.of(user));
        when(twoFactorService.verifyLoginCode(user, "123456")).thenReturn(true);
        when(authenticationService.generateAuthToken(eq(user), any(), any())).thenReturn("real-token");

        ResponseEntity<Map<String, Object>> response =
            controller.loginTwoFactor(Map.of("userId", 5, "code", "123456"), new MockHttpServletRequest());

        assertEquals(200, response.getStatusCode().value());
        assertEquals("real-token", response.getBody().get("token"));
        verify(twoFactorAttemptService).recordSuccess(5L);
    }

    @Test
    void twoFactorLoginStepRejectsAWrongCodeAndRecordsAFailure() {
        User user = userWithTwoFactor(true);
        when(twoFactorAttemptService.isBlocked(5L)).thenReturn(false);
        when(authenticationService.findById(5L)).thenReturn(Optional.of(user));
        when(twoFactorService.verifyLoginCode(user, "000000")).thenReturn(false);

        ResponseEntity<Map<String, Object>> response =
            controller.loginTwoFactor(Map.of("userId", 5, "code", "000000"), new MockHttpServletRequest());

        assertEquals(400, response.getStatusCode().value());
        verify(twoFactorAttemptService).recordFailure(5L);
        verify(authenticationService, never()).generateAuthToken(any());
    }

    @Test
    void twoFactorLoginStepIsBlockedAfterTooManyFailures() {
        when(twoFactorAttemptService.isBlocked(5L)).thenReturn(true);
        when(twoFactorAttemptService.retryAfterMinutes(5L)).thenReturn(7L);

        ResponseEntity<Map<String, Object>> response =
            controller.loginTwoFactor(Map.of("userId", 5, "code", "123456"), new MockHttpServletRequest());

        assertEquals(429, response.getStatusCode().value());
        verify(twoFactorService, never()).verifyLoginCode(any(), anyString());
    }

    @Test
    void twoFactorLoginStepRefusesAnAccountThatDoesNotHaveTwoFactorEnabled() {
        User user = userWithTwoFactor(false);
        when(twoFactorAttemptService.isBlocked(5L)).thenReturn(false);
        when(authenticationService.findById(5L)).thenReturn(Optional.of(user));

        ResponseEntity<Map<String, Object>> response =
            controller.loginTwoFactor(Map.of("userId", 5, "code", "123456"), new MockHttpServletRequest());

        assertEquals(400, response.getStatusCode().value());
        verify(twoFactorService, never()).verifyLoginCode(any(), anyString());
    }
}
