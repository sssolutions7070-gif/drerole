package com.jobera.service;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jobera.entity.PasswordResetToken;
import com.jobera.entity.User;
import com.jobera.repository.PasswordResetTokenRepository;
import com.jobera.repository.UserRepository;
import com.jobera.util.PasswordEncoder;
import com.jobera.util.TokenHasher;

@Service
public class PasswordResetService {

    private static final long TOKEN_VALIDITY_MINUTES = 30;

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final AuthenticationService authenticationService;
    private final NotificationService notificationService;
    private final PasswordEncoder passwordEncoder = new PasswordEncoder();

    public PasswordResetService(UserRepository userRepository,
                                PasswordResetTokenRepository resetTokenRepository,
                                AuthenticationService authenticationService,
                                NotificationService notificationService) {
        this.userRepository = userRepository;
        this.resetTokenRepository = resetTokenRepository;
        this.authenticationService = authenticationService;
        this.notificationService = notificationService;
    }

    /**
     * Always succeeds from the caller's point of view (never reveals whether the email exists).
     * If a matching, active account exists, emails it a single-use reset link.
     */
    public void requestReset(String email, String frontendBaseUrl) {
        if (email == null || email.isBlank()) {
            return;
        }
        Optional<User> userOpt = userRepository.findByEmail(email.trim());
        if (userOpt.isEmpty() || Boolean.FALSE.equals(userOpt.get().getIsActive())) {
            return;
        }
        User user = userOpt.get();

        String rawToken = TokenHasher.generate();
        PasswordResetToken token = new PasswordResetToken(
            user, TokenHasher.hash(rawToken), LocalDateTime.now().plusMinutes(TOKEN_VALIDITY_MINUTES));
        resetTokenRepository.save(token);

        String link = frontendBaseUrl + "/reset-password?token=" + rawToken;
        notificationService.sendPasswordReset(user.getEmail(), link);
    }

    public static class InvalidResetTokenException extends RuntimeException {
        public InvalidResetTokenException(String message) {
            super(message);
        }
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidResetTokenException("Reset link is invalid or has expired");
        }
        PasswordResetToken token = resetTokenRepository.findByToken(TokenHasher.hash(rawToken))
            .orElseThrow(() -> new InvalidResetTokenException("Reset link is invalid or has expired"));

        if (Boolean.TRUE.equals(token.getUsed()) || token.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new InvalidResetTokenException("Reset link is invalid or has expired");
        }

        User user = token.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        token.setUsed(true);
        resetTokenRepository.save(token);

        // A password reset is a good reason to sign every other session out too.
        authenticationService.logoutAllUserSessions(user.getId());
    }
}
