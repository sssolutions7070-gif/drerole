package com.jobera.service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobera.entity.User;
import com.jobera.repository.UserRepository;
import com.jobera.util.AesGcmCipher;
import com.jobera.util.PasswordEncoder;
import com.jobera.util.TotpService;

/**
 * TOTP-based two-factor authentication: setup (with a confirmation step so an account can't be
 * locked out by a typo'd secret), disable, and verifying a code (TOTP or a one-time backup code)
 * at login. See AesGcmCipher for why the seed is encrypted rather than hashed, and TotpService
 * for the RFC 6238 mechanics.
 */
@Service
public class TwoFactorService {

    private static final int BACKUP_CODE_COUNT = 8;
    private static final String ISSUER = "DreamRole";

    public static class TwoFactorException extends RuntimeException {
        public TwoFactorException(String message) {
            super(message);
        }
    }

    private final UserRepository userRepository;
    private final String base64Key;
    private final TotpService totpService = new TotpService();
    private final PasswordEncoder passwordEncoder = new PasswordEncoder();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SecureRandom random = new SecureRandom();

    public TwoFactorService(UserRepository userRepository,
                             @Value("${app.two-factor-key:}") String base64Key) {
        this.userRepository = userRepository;
        this.base64Key = base64Key;
    }

    /** Deferred rather than a bean built at startup, so a server that never configures 2FA isn't
     *  broken for every other feature - the failure only happens if/when 2FA is actually used. */
    private AesGcmCipher cipher() {
        if (base64Key == null || base64Key.isBlank()) {
            throw new TwoFactorException("Two-factor authentication is not configured on this server");
        }
        try {
            return AesGcmCipher.fromBase64Key(base64Key);
        } catch (Exception e) {
            throw new TwoFactorException("Two-factor authentication is misconfigured on this server");
        }
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new TwoFactorException("User not found"));
    }

    /** Step 1: a fresh secret and its otpauth:// URI, not persisted until confirmSetup() succeeds. */
    public Map<String, String> beginSetup(Long userId) {
        User user = getUser(userId);
        String secret = totpService.generateSecret();
        Map<String, String> result = new LinkedHashMap<>();
        result.put("secret", secret);
        result.put("otpauthUri", totpService.buildOtpAuthUri(secret, user.getEmail(), ISSUER));
        return result;
    }

    /** Step 2: proves the user actually captured the secret correctly before it's turned on.
     *  Returns the plaintext backup codes - shown to the user exactly once. */
    public List<String> confirmSetup(Long userId, String secret, String code) {
        User user = getUser(userId);
        if (!totpService.verifyCode(secret, code)) {
            throw new TwoFactorException("Incorrect code - check your authenticator app and try again");
        }

        List<String> backupCodes = generateBackupCodes();
        user.setTwoFactorSecretEncrypted(cipher().encrypt(secret));
        user.setTwoFactorBackupCodes(writeBackupCodes(hashAll(backupCodes)));
        user.setTwoFactorEnabled(true);
        userRepository.save(user);
        return backupCodes;
    }

    public void disable(Long userId, String password) {
        User user = getUser(userId);
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new TwoFactorException("Password is incorrect");
        }
        user.setTwoFactorEnabled(false);
        user.setTwoFactorSecretEncrypted(null);
        user.setTwoFactorBackupCodes(null);
        userRepository.save(user);
    }

    /** TOTP first, then falls back to a one-time backup code (which is consumed on use). */
    public boolean verifyLoginCode(User user, String code) {
        if (!Boolean.TRUE.equals(user.getTwoFactorEnabled()) || code == null || code.isBlank()) {
            return false;
        }
        String trimmed = code.trim();

        String secret = cipher().decrypt(user.getTwoFactorSecretEncrypted());
        if (totpService.verifyCode(secret, trimmed)) {
            return true;
        }
        return tryConsumeBackupCode(user, trimmed);
    }

    private boolean tryConsumeBackupCode(User user, String candidateCode) {
        List<String> hashedCodes = readBackupCodes(user.getTwoFactorBackupCodes());
        for (int i = 0; i < hashedCodes.size(); i++) {
            if (passwordEncoder.matches(candidateCode, hashedCodes.get(i))) {
                hashedCodes.remove(i);
                user.setTwoFactorBackupCodes(writeBackupCodes(hashedCodes));
                userRepository.save(user);
                return true;
            }
        }
        return false;
    }

    private List<String> generateBackupCodes() {
        List<String> codes = new ArrayList<>(BACKUP_CODE_COUNT);
        for (int i = 0; i < BACKUP_CODE_COUNT; i++) {
            codes.add(String.format(Locale.ROOT, "%08d", random.nextInt(100_000_000)));
        }
        return codes;
    }

    private List<String> hashAll(List<String> plainCodes) {
        List<String> hashed = new ArrayList<>(plainCodes.size());
        for (String code : plainCodes) {
            hashed.add(passwordEncoder.encode(code));
        }
        return hashed;
    }

    private List<String> readBackupCodes(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return new ArrayList<>(objectMapper.readValue(json, new TypeReference<List<String>>() {}));
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private String writeBackupCodes(List<String> hashedCodes) {
        try {
            return objectMapper.writeValueAsString(hashedCodes);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize backup codes", e);
        }
    }
}
