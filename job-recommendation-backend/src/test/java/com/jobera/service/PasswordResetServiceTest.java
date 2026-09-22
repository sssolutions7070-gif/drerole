package com.jobera.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jobera.entity.PasswordResetToken;
import com.jobera.entity.User;
import com.jobera.repository.PasswordResetTokenRepository;
import com.jobera.repository.UserRepository;
import com.jobera.util.PasswordEncoder;
import com.jobera.util.TokenHasher;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordResetTokenRepository resetTokenRepository;
    @Mock AuthenticationService authenticationService;
    @Mock NotificationService notificationService;

    PasswordResetService service;
    final PasswordEncoder encoder = new PasswordEncoder();

    @BeforeEach
    void setUp() {
        service = new PasswordResetService(userRepository, resetTokenRepository, authenticationService, notificationService);
    }

    private User activeUser() {
        User user = new User();
        user.setId(5L);
        user.setEmail("a@b.com");
        user.setIsActive(true);
        user.setPassword(encoder.encode("old-password"));
        return user;
    }

    @Test
    void requestResetEmailsALinkForAKnownActiveAccount() {
        User user = activeUser();
        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(user));

        service.requestReset("a@b.com", "http://localhost:3000");

        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(resetTokenRepository).save(tokenCaptor.capture());
        assertEquals(user, tokenCaptor.getValue().getUser());
        assertTrue(tokenCaptor.getValue().getExpiryDate().isAfter(LocalDateTime.now()));

        verify(notificationService).sendPasswordReset(eq("a@b.com"), contains("http://localhost:3000/reset-password?token="));
    }

    @Test
    void requestResetDoesNothingObservableForAnUnknownEmail() {
        when(userRepository.findByEmail("nobody@b.com")).thenReturn(Optional.empty());

        service.requestReset("nobody@b.com", "http://localhost:3000");

        verify(resetTokenRepository, never()).save(any());
        verify(notificationService, never()).sendPasswordReset(anyString(), anyString());
    }

    @Test
    void requestResetDoesNothingForADeactivatedAccount() {
        User user = activeUser();
        user.setIsActive(false);
        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(user));

        service.requestReset("a@b.com", "http://localhost:3000");

        verify(resetTokenRepository, never()).save(any());
    }

    @Test
    void resetPasswordUpdatesThePasswordAndInvalidatesOtherSessions() {
        User user = activeUser();
        String rawToken = "raw-token-value";
        PasswordResetToken token = new PasswordResetToken(user, TokenHasher.hash(rawToken), LocalDateTime.now().plusMinutes(30));
        when(resetTokenRepository.findByToken(TokenHasher.hash(rawToken))).thenReturn(Optional.of(token));

        service.resetPassword(rawToken, "brand-new-password");

        assertTrue(encoder.matches("brand-new-password", user.getPassword()));
        assertNotEquals("old-password", user.getPassword());
        verify(userRepository).save(user);
        verify(authenticationService).logoutAllUserSessions(5L);

        ArgumentCaptor<PasswordResetToken> savedToken = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(resetTokenRepository, times(1)).save(savedToken.capture());
        assertTrue(savedToken.getValue().getUsed());
    }

    @Test
    void resetPasswordRejectsAnUnknownToken() {
        when(resetTokenRepository.findByToken(anyString())).thenReturn(Optional.empty());

        assertThrows(PasswordResetService.InvalidResetTokenException.class,
            () -> service.resetPassword("nope", "brand-new-password"));
    }

    @Test
    void resetPasswordRejectsAnExpiredToken() {
        User user = activeUser();
        String rawToken = "raw-token-value";
        PasswordResetToken token = new PasswordResetToken(user, TokenHasher.hash(rawToken), LocalDateTime.now().minusMinutes(1));
        when(resetTokenRepository.findByToken(TokenHasher.hash(rawToken))).thenReturn(Optional.of(token));

        assertThrows(PasswordResetService.InvalidResetTokenException.class,
            () -> service.resetPassword(rawToken, "brand-new-password"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void resetPasswordRejectsAnAlreadyUsedToken() {
        User user = activeUser();
        String rawToken = "raw-token-value";
        PasswordResetToken token = new PasswordResetToken(user, TokenHasher.hash(rawToken), LocalDateTime.now().plusMinutes(30));
        token.setUsed(true);
        when(resetTokenRepository.findByToken(TokenHasher.hash(rawToken))).thenReturn(Optional.of(token));

        assertThrows(PasswordResetService.InvalidResetTokenException.class,
            () -> service.resetPassword(rawToken, "brand-new-password"));
    }
}
