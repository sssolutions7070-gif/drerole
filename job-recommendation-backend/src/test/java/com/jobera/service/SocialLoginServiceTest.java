package com.jobera.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jobera.entity.User;
import com.jobera.repository.UserRepository;
import com.jobera.service.OAuthService.OAuthException;
import com.jobera.service.OAuthService.OAuthUserInfo;

@ExtendWith(MockitoExtension.class)
class SocialLoginServiceTest {

    @Mock UserRepository userRepository;
    @Mock ReferralService referralService;

    SocialLoginService service;

    @BeforeEach
    void setUp() {
        service = new SocialLoginService(userRepository, referralService);
        lenient().when(referralService.generateUniqueCode()).thenReturn("ABCD1234");
        lenient().when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void aBrandNewEmailCreatesANewLinkedAccount() {
        when(userRepository.findByEmailIgnoreCase("person@example.com")).thenReturn(Optional.empty());
        OAuthUserInfo info = new OAuthUserInfo("g-1", "person@example.com", true, "Person One");

        User user = service.findOrCreateUser("google", info);

        assertEquals("person@example.com", user.getEmail());
        assertEquals("Person One", user.getFullName());
        assertEquals("google", user.getOauthProvider());
        assertEquals("g-1", user.getOauthProviderId());
        assertEquals("ABCD1234", user.getReferralCode());
        assertEquals(1L, user.getRoleId());
        assertNotNull(user.getPassword());
        verify(userRepository).save(user);
    }

    @Test
    void anExistingAccountWithNoProviderYetGetsLinkedInPlace() {
        User existing = new User("person@example.com", "existing-hash", "Existing Person");
        existing.setId(5L);
        existing.setIsActive(true);
        when(userRepository.findByEmailIgnoreCase("person@example.com")).thenReturn(Optional.of(existing));
        OAuthUserInfo info = new OAuthUserInfo("g-1", "person@example.com", true, "Existing Person");

        User result = service.findOrCreateUser("google", info);

        assertEquals(existing, result);
        assertEquals("google", existing.getOauthProvider());
        assertEquals("g-1", existing.getOauthProviderId());
        verify(userRepository).save(existing);
    }

    @Test
    void anAccountAlreadyLinkedToAProviderIsReusedWithoutResavingTheLink() {
        User existing = new User("person@example.com", "existing-hash", "Existing Person");
        existing.setId(5L);
        existing.setIsActive(true);
        existing.setOauthProvider("google");
        existing.setOauthProviderId("g-1");
        when(userRepository.findByEmailIgnoreCase("person@example.com")).thenReturn(Optional.of(existing));
        OAuthUserInfo info = new OAuthUserInfo("g-1", "person@example.com", true, "Existing Person");

        User result = service.findOrCreateUser("google", info);

        assertEquals(existing, result);
        verify(userRepository, never()).save(any());
    }

    @Test
    void aDeactivatedAccountCannotSignInThroughAProvider() {
        User existing = new User("person@example.com", "existing-hash", "Existing Person");
        existing.setId(5L);
        existing.setIsActive(false);
        when(userRepository.findByEmailIgnoreCase("person@example.com")).thenReturn(Optional.of(existing));
        OAuthUserInfo info = new OAuthUserInfo("g-1", "person@example.com", true, "Existing Person");

        assertThrows(OAuthException.class, () -> service.findOrCreateUser("google", info));
        verify(userRepository, never()).save(any());
    }

    @Test
    void anUnverifiedProviderEmailIsRejected() {
        OAuthUserInfo info = new OAuthUserInfo("g-1", "person@example.com", false, "Person One");

        assertThrows(OAuthException.class, () -> service.findOrCreateUser("google", info));
        verify(userRepository, never()).findByEmailIgnoreCase(anyString());
    }

    @Test
    void aMissingProviderEmailIsRejected() {
        OAuthUserInfo info = new OAuthUserInfo("g-1", null, true, "Person One");

        assertThrows(OAuthException.class, () -> service.findOrCreateUser("google", info));
        verify(userRepository, never()).findByEmailIgnoreCase(anyString());
    }

    @Test
    void aBlankProviderEmailIsRejected() {
        OAuthUserInfo info = new OAuthUserInfo("g-1", "  ", true, "Person One");

        assertThrows(OAuthException.class, () -> service.findOrCreateUser("google", info));
    }
}
