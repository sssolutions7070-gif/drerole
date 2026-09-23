package com.jobera.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jobera.entity.User;
import com.jobera.repository.UserRepository;
import com.jobera.util.PasswordEncoder;
import com.jobera.util.TotpService;

@ExtendWith(MockitoExtension.class)
class TwoFactorServiceTest {

    @Mock UserRepository userRepository;

    TotpService totpService;
    User user;

    private static String randomBase64Key() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    private TwoFactorService serviceWithKey(String key) {
        return new TwoFactorService(userRepository, key);
    }

    @BeforeEach
    void setUp() {
        totpService = new TotpService();
        user = new User("candidate@example.com", new PasswordEncoder().encode("CorrectPassword1!"), "Candidate");
        user.setId(1L);
        lenient().when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        lenient().when(userRepository.save(org.mockito.ArgumentMatchers.any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void beginSetupReturnsAFreshSecretAndAnOtpauthUri() {
        TwoFactorService service = serviceWithKey(randomBase64Key());

        Map<String, String> setup = service.beginSetup(1L);

        assertTrue(setup.get("secret").matches("[A-Z2-7]+"));
        assertTrue(setup.get("otpauthUri").contains("candidate%40example.com"));
        assertFalse(Boolean.TRUE.equals(user.getTwoFactorEnabled()), "should not be enabled until confirmed");
    }

    @Test
    void confirmSetupEnablesTwoFactorAndReturnsUsableBackupCodes() {
        TwoFactorService service = serviceWithKey(randomBase64Key());
        Map<String, String> setup = service.beginSetup(1L);
        String validCode = totpService.currentCode(setup.get("secret"));

        List<String> backupCodes = service.confirmSetup(1L, setup.get("secret"), validCode);

        assertTrue(user.getTwoFactorEnabled());
        assertEquals(8, backupCodes.size());
        assertNotEquals(setup.get("secret"), user.getTwoFactorSecretEncrypted(), "must be stored encrypted, not in plain text");
    }

    @Test
    void confirmSetupRejectsAWrongCodeAndLeavesTwoFactorOff() {
        TwoFactorService service = serviceWithKey(randomBase64Key());
        Map<String, String> setup = service.beginSetup(1L);

        assertThrows(TwoFactorService.TwoFactorException.class,
            () -> service.confirmSetup(1L, setup.get("secret"), "000000"));
        assertFalse(Boolean.TRUE.equals(user.getTwoFactorEnabled()));
    }

    @Test
    void aFreshlyIssuedTotpCodeVerifiesAtLogin() {
        TwoFactorService service = serviceWithKey(randomBase64Key());
        Map<String, String> setup = service.beginSetup(1L);
        String setupCode = totpService.currentCode(setup.get("secret"));
        service.confirmSetup(1L, setup.get("secret"), setupCode);

        String loginCode = totpService.currentCode(setup.get("secret"));

        assertTrue(service.verifyLoginCode(user, loginCode));
    }

    @Test
    void aBackupCodeWorksOnceAndIsThenConsumed() {
        TwoFactorService service = serviceWithKey(randomBase64Key());
        Map<String, String> setup = service.beginSetup(1L);
        String setupCode = totpService.currentCode(setup.get("secret"));
        List<String> backupCodes = service.confirmSetup(1L, setup.get("secret"), setupCode);
        String backupCode = backupCodes.get(0);

        assertTrue(service.verifyLoginCode(user, backupCode), "backup code should work the first time");
        assertFalse(service.verifyLoginCode(user, backupCode), "the same backup code must not work twice");
    }

    @Test
    void verifyLoginCodeRejectsWhenTwoFactorIsNotEnabled() {
        TwoFactorService service = serviceWithKey(randomBase64Key());

        assertFalse(service.verifyLoginCode(user, "123456"));
    }

    @Test
    void disableRequiresTheCorrectPasswordAndClearsEverything() {
        TwoFactorService service = serviceWithKey(randomBase64Key());
        Map<String, String> setup = service.beginSetup(1L);
        String setupCode = totpService.currentCode(setup.get("secret"));
        service.confirmSetup(1L, setup.get("secret"), setupCode);

        assertThrows(TwoFactorService.TwoFactorException.class, () -> service.disable(1L, "wrong-password"));
        assertTrue(user.getTwoFactorEnabled(), "a wrong password must not disable it");

        service.disable(1L, "CorrectPassword1!");

        assertFalse(user.getTwoFactorEnabled());
        assertNull(user.getTwoFactorSecretEncrypted());
        assertNull(user.getTwoFactorBackupCodes());
    }

    @Test
    void refusesToOperateWhenNoEncryptionKeyIsConfigured() {
        TwoFactorService service = serviceWithKey("");

        Map<String, String> setup = service.beginSetup(1L); // doesn't need the key yet
        String code = totpService.currentCode(setup.get("secret"));

        assertThrows(TwoFactorService.TwoFactorException.class,
            () -> service.confirmSetup(1L, setup.get("secret"), code));
    }
}
