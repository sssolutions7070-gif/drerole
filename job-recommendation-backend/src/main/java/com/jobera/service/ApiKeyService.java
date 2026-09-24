package com.jobera.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.jobera.entity.ApiKey;
import com.jobera.entity.Recruiter;
import com.jobera.entity.User;
import com.jobera.repository.ApiKeyRepository;
import com.jobera.repository.RecruiterRepository;

/**
 * Lets a recruiter generate credentials for their own integrations (a script, their ATS, Zapier)
 * to call the DreamRole API as themselves, without a browser session. Restricted to the account
 * owner, not team members - minting a credential with full API access is an account-level action,
 * unlike the day-to-day recruiting actions team members already share (see RecruiterTeamService).
 */
@Service
public class ApiKeyService {

    private static final String KEY_PREFIX = "dr_live_";
    private static final int KEY_RANDOM_BYTES = 24;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ApiKeyRepository apiKeyRepository;
    private final RecruiterRepository recruiterRepository;

    public ApiKeyService(ApiKeyRepository apiKeyRepository, RecruiterRepository recruiterRepository) {
        this.apiKeyRepository = apiKeyRepository;
        this.recruiterRepository = recruiterRepository;
    }

    public static class RecruiterNotFoundException extends RuntimeException {
        public RecruiterNotFoundException(Long recruiterId) {
            super("Recruiter not found: " + recruiterId);
        }
    }

    public static class NotTheAccountOwnerException extends RuntimeException {
        public NotTheAccountOwnerException() {
            super("Only the account owner can manage API keys");
        }
    }

    public static class ApiKeyNotFoundException extends RuntimeException {
        public ApiKeyNotFoundException(Long keyId) {
            super("API key not found: " + keyId);
        }
    }

    private void requireOwner(Recruiter recruiter, Long callerId) {
        if (recruiter.getUser() == null || !recruiter.getUser().getId().equals(callerId)) {
            throw new NotTheAccountOwnerException();
        }
    }

    /** Returns the plaintext key under "key" - the only time it's ever available. */
    public Map<String, Object> generateKey(Long recruiterId, Long callerId, String label) {
        Recruiter recruiter = recruiterRepository.findById(recruiterId)
            .orElseThrow(() -> new RecruiterNotFoundException(recruiterId));
        requireOwner(recruiter, callerId);

        byte[] randomBytes = new byte[KEY_RANDOM_BYTES];
        RANDOM.nextBytes(randomBytes);
        String plaintextKey = KEY_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        String hash = sha256Hex(plaintextKey);
        String displayPrefix = plaintextKey.substring(0, Math.min(plaintextKey.length(), 12)) + "...";

        ApiKey saved = apiKeyRepository.save(new ApiKey(recruiterId, hash, displayPrefix, label));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", saved.getId());
        response.put("key", plaintextKey);
        response.put("keyPrefix", saved.getKeyPrefix());
        response.put("label", saved.getLabel());
        response.put("createdAt", saved.getCreatedAt());
        return response;
    }

    public List<Map<String, Object>> listKeys(Long recruiterId) {
        return apiKeyRepository.findByRecruiterIdOrderByCreatedAtDesc(recruiterId).stream()
            .map(this::keyView)
            .toList();
    }

    private Map<String, Object> keyView(ApiKey key) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", key.getId());
        view.put("keyPrefix", key.getKeyPrefix());
        view.put("label", key.getLabel());
        view.put("createdAt", key.getCreatedAt());
        view.put("lastUsedAt", key.getLastUsedAt());
        view.put("revoked", key.isRevoked());
        return view;
    }

    public void revokeKey(Long recruiterId, Long keyId, Long callerId) {
        Recruiter recruiter = recruiterRepository.findById(recruiterId)
            .orElseThrow(() -> new RecruiterNotFoundException(recruiterId));
        requireOwner(recruiter, callerId);

        ApiKey key = apiKeyRepository.findByIdAndRecruiterId(keyId, recruiterId)
            .orElseThrow(() -> new ApiKeyNotFoundException(keyId));
        key.setRevoked(true);
        apiKeyRepository.save(key);
    }

    /** Resolves a raw "X-API-Key" header value to the recruiter account it belongs to, or empty
     *  if it's missing, unknown, or revoked. Called from AuthInterceptor as an alternative to a
     *  bearer token - a valid key authenticates exactly as the recruiter who generated it. */
    public Optional<User> authenticate(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return Optional.empty();
        }
        String hash = sha256Hex(rawKey.trim());
        return apiKeyRepository.findByKeyHashAndRevokedFalse(hash).flatMap(key -> {
            key.setLastUsedAt(LocalDateTime.now());
            apiKeyRepository.save(key);
            return recruiterRepository.findById(key.getRecruiterId()).map(Recruiter::getUser);
        });
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
