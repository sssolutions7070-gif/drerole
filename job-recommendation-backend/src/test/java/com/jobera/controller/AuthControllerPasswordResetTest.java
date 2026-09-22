package com.jobera.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
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

import com.jobera.security.LoginAttemptService;
import com.jobera.security.PasswordResetRateLimiter;
import com.jobera.service.AuthenticationService;
import com.jobera.service.NotificationService;
import com.jobera.service.PasswordResetService;

@ExtendWith(MockitoExtension.class)
class AuthControllerPasswordResetTest {

    @Mock AuthenticationService authenticationService;
    @Mock LoginAttemptService loginAttemptService;
    @Mock PasswordResetService passwordResetService;
    @Mock PasswordResetRateLimiter passwordResetRateLimiter;
    @Mock NotificationService notificationService;

    AuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthController(authenticationService, loginAttemptService, passwordResetService,
            passwordResetRateLimiter, notificationService, "http://localhost:3000");
    }

    @Test
    void forgotPasswordAlwaysReturnsTheSameGenericMessage() {
        when(passwordResetRateLimiter.isBlocked("a@b.com")).thenReturn(false);

        ResponseEntity<Map<String, String>> response = controller.forgotPassword(Map.of("email", "a@b.com"));

        assertEquals(200, response.getStatusCode().value());
        assertEquals("If an account exists for that email, a reset link has been sent.",
            response.getBody().get("message"));
        verify(passwordResetService).requestReset("a@b.com", "http://localhost:3000");
    }

    @Test
    void forgotPasswordSkipsTheServiceCallWhenRateLimited() {
        when(passwordResetRateLimiter.isBlocked("a@b.com")).thenReturn(true);

        ResponseEntity<Map<String, String>> response = controller.forgotPassword(Map.of("email", "a@b.com"));

        assertEquals(200, response.getStatusCode().value());
        verify(passwordResetService, never()).requestReset(anyString(), anyString());
    }

    @Test
    void resetPasswordRejectsAShortPasswordWithoutTouchingTheService() {
        ResponseEntity<Map<String, String>> response = controller.resetPassword(Map.of("token", "t", "password", "short"));

        assertEquals(400, response.getStatusCode().value());
        verify(passwordResetService, never()).resetPassword(anyString(), anyString());
    }

    @Test
    void resetPasswordSucceedsWithAValidTokenAndLongEnoughPassword() {
        ResponseEntity<Map<String, String>> response =
            controller.resetPassword(Map.of("token", "good-token", "password", "new-password-123"));

        assertEquals(200, response.getStatusCode().value());
        verify(passwordResetService).resetPassword("good-token", "new-password-123");
    }

    @Test
    void resetPasswordReturns400ForAnInvalidToken() {
        doThrow(new PasswordResetService.InvalidResetTokenException("Reset link is invalid or has expired"))
            .when(passwordResetService).resetPassword(anyString(), anyString());

        ResponseEntity<Map<String, String>> response =
            controller.resetPassword(Map.of("token", "bad-token", "password", "new-password-123"));

        assertEquals(400, response.getStatusCode().value());
        assertEquals("Reset link is invalid or has expired", response.getBody().get("error"));
    }
}
