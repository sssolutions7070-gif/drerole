package com.jobera.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jobera.entity.ApiKey;
import com.jobera.entity.Recruiter;
import com.jobera.entity.User;
import com.jobera.repository.ApiKeyRepository;
import com.jobera.repository.RecruiterRepository;

@ExtendWith(MockitoExtension.class)
class ApiKeyServiceTest {

    @Mock ApiKeyRepository apiKeyRepository;
    @Mock RecruiterRepository recruiterRepository;

    ApiKeyService service;

    User owner;
    Recruiter recruiter;

    @BeforeEach
    void setUp() {
        service = new ApiKeyService(apiKeyRepository, recruiterRepository);
        owner = new User();
        owner.setId(1L);
        recruiter = new Recruiter();
        recruiter.setId(100L);
        recruiter.setUser(owner);
        lenient().when(recruiterRepository.findById(100L)).thenReturn(Optional.of(recruiter));
        lenient().when(apiKeyRepository.save(any())).thenAnswer(inv -> {
            ApiKey key = inv.getArgument(0);
            key.setId(500L);
            return key;
        });
    }

    @Test
    void ownerCanGenerateAKey() {
        Map<String, Object> result = service.generateKey(100L, 1L, "My integration");

        assertTrue(((String) result.get("key")).startsWith("dr_live_"));
        assertEquals("My integration", result.get("label"));
        verify(apiKeyRepository).save(any());
    }

    @Test
    void theStoredKeyPrefixFitsTheDatabaseColumn() {
        // ApiKey.keyPrefix is a VARCHAR(32) column - the displayed prefix must never exceed it,
        // regardless of how long the generated plaintext key is.
        Map<String, Object> result = service.generateKey(100L, 1L, "label");

        assertTrue(((String) result.get("keyPrefix")).length() <= 32);
    }

    @Test
    void nonOwnerCannotGenerateAKey() {
        assertThrows(ApiKeyService.NotTheAccountOwnerException.class,
            () -> service.generateKey(100L, 2L, "label"));
    }

    @Test
    void aGeneratedKeyLaterAuthenticatesToTheOwner() {
        Map<String, Object> result = service.generateKey(100L, 1L, "label");
        String plaintextKey = (String) result.get("key");

        ApiKey stored = new ApiKey(100L, sha256(plaintextKey), "dr_live_...", "label");
        when(apiKeyRepository.findByKeyHashAndRevokedFalse(sha256(plaintextKey))).thenReturn(Optional.of(stored));

        Optional<User> authenticated = service.authenticate(plaintextKey);

        assertTrue(authenticated.isPresent());
        assertEquals(1L, authenticated.get().getId());
    }

    @Test
    void authenticateReturnsEmptyForAnUnknownKey() {
        when(apiKeyRepository.findByKeyHashAndRevokedFalse(any())).thenReturn(Optional.empty());

        assertFalse(service.authenticate("dr_live_unknown").isPresent());
    }

    @Test
    void authenticateReturnsEmptyForABlankKey() {
        assertFalse(service.authenticate("").isPresent());
        assertFalse(service.authenticate(null).isPresent());
    }

    @Test
    void ownerCanRevokeAKey() {
        ApiKey key = new ApiKey(100L, "hash", "prefix", "label");
        key.setId(500L);
        when(apiKeyRepository.findByIdAndRecruiterId(500L, 100L)).thenReturn(Optional.of(key));

        service.revokeKey(100L, 500L, 1L);

        assertTrue(key.isRevoked());
    }

    @Test
    void nonOwnerCannotRevokeAKey() {
        assertThrows(ApiKeyService.NotTheAccountOwnerException.class,
            () -> service.revokeKey(100L, 500L, 2L));
    }

    private String sha256(String value) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
