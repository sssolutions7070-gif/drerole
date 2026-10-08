package com.jobera.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jobera.entity.AuthToken;
import com.jobera.entity.User;
import com.jobera.repository.AuthTokenRepository;
import com.jobera.repository.UserRepository;
import com.jobera.util.PasswordEncoder;
import com.jobera.util.PasswordEncoderTest;
import com.jobera.util.TokenHasher;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock UserRepository userRepository;
    @Mock AuthTokenRepository authTokenRepository;
    @Mock ReferralService referralService;

    AuthenticationService service;
    final PasswordEncoder encoder = new PasswordEncoder();

    @BeforeEach
    void setUp() {
        service = new AuthenticationService(userRepository, authTokenRepository, referralService);
    }

    private User userWithPassword(String encodedPassword) {
        User user = new User();
        user.setId(7L);
        user.setEmail("a@b.com");
        user.setPassword(encodedPassword);
        user.setIsActive(true);
        return user;
    }

    @Test
    void loginUpgradesLegacyHashToPbkdf2() throws Exception {
        User user = userWithPassword(PasswordEncoderTest.legacyHash("hunter2"));
        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(user));

        User result = service.loginUser("a@b.com", "hunter2");

        assertTrue(result.getPassword().startsWith("pbkdf2_sha256$"));
        assertTrue(encoder.matches("hunter2", result.getPassword()));
        assertNotNull(result.getLastLogin());
        verify(userRepository).save(user);
    }

    @Test
    void loginKeepsAnUpToDateHashUnchanged() {
        String hash = encoder.encode("hunter2");
        User user = userWithPassword(hash);
        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(user));

        service.loginUser("a@b.com", "hunter2");

        assertEquals(hash, user.getPassword());
    }

    @Test
    void wrongPasswordIsRejected() {
        User user = userWithPassword(encoder.encode("hunter2"));
        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(user));

        assertThrows(AuthenticationService.AuthenticationException.class,
            () -> service.loginUser("a@b.com", "nope"));
    }

    @Test
    void unknownEmailIsRejected() {
        when(userRepository.findByEmail("x@y.com")).thenReturn(Optional.empty());

        assertThrows(AuthenticationService.AuthenticationException.class,
            () -> service.loginUser("x@y.com", "whatever"));
    }

    @Test
    void deactivatedAccountCannotLogIn() {
        User user = userWithPassword(encoder.encode("hunter2"));
        user.setIsActive(false);
        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(user));

        assertThrows(AuthenticationService.AuthenticationException.class,
            () -> service.loginUser("a@b.com", "hunter2"));
    }

    @Test
    void registrationStoresAPbkdf2Hash() {
        when(userRepository.findByEmail("new@b.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(referralService.generateUniqueCode()).thenReturn("ABCD1234");

        User created = service.registerUser("new@b.com", "s3cret!", "New User", "123");

        assertFalse(created.getPassword().equals("s3cret!"));
        assertTrue(encoder.matches("s3cret!", created.getPassword()));
    }

    @Test
    void registrationGeneratesAReferralCodeAndRecordsAnyReferrerGiven() {
        when(userRepository.findByEmail("new@b.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(referralService.generateUniqueCode()).thenReturn("ABCD1234");

        User created = service.registerUser("new@b.com", "s3cret!", "New User", "123", "REFCODE1");

        assertEquals("ABCD1234", created.getReferralCode());
        verify(referralService).recordReferralIfValid("REFCODE1", created);
    }

    @Test
    void generateAuthTokenRecordsTheDeviceAndIp() {
        User user = userWithPassword("hash");

        service.generateAuthToken(user, "Mozilla/5.0 Chrome/120", "10.0.0.1");

        ArgumentCaptor<AuthToken> captor = ArgumentCaptor.forClass(AuthToken.class);
        verify(authTokenRepository).save(captor.capture());
        assertEquals("Mozilla/5.0 Chrome/120", captor.getValue().getUserAgent());
        assertEquals("10.0.0.1", captor.getValue().getIpAddress());
    }

    @Test
    void validatingAValidTokenTouchesLastUsedAt() {
        AuthToken token = new AuthToken(userWithPassword("hash"), TokenHasher.hash("raw"), LocalDateTime.now().plusDays(1));
        when(authTokenRepository.findByToken(TokenHasher.hash("raw"))).thenReturn(Optional.of(token));

        service.validateToken("raw");

        ArgumentCaptor<AuthToken> captor = ArgumentCaptor.forClass(AuthToken.class);
        verify(authTokenRepository).save(captor.capture());
        assertNotNull(captor.getValue().getLastUsedAt());
    }

    @Test
    void validatingAnExpiredTokenDeletesItAndReturnsEmpty() {
        AuthToken token = new AuthToken(userWithPassword("hash"), TokenHasher.hash("raw"), LocalDateTime.now().minusDays(1));
        when(authTokenRepository.findByToken(TokenHasher.hash("raw"))).thenReturn(Optional.of(token));

        Optional<User> result = service.validateToken("raw");

        assertTrue(result.isEmpty());
        verify(authTokenRepository).delete(token);
    }

    @Test
    void listSessionsReturnsOnlyThisUsersActiveSessions() {
        User user = userWithPassword("hash");
        AuthToken token = new AuthToken(user, "h", LocalDateTime.now().plusDays(1));
        when(authTokenRepository.findByUserIdAndExpiryDateAfterOrderByLastUsedAtDesc(any(), any()))
            .thenReturn(List.of(token));

        List<AuthToken> sessions = service.listSessions(7L);

        assertEquals(1, sessions.size());
    }

    @Test
    void revokingAnOwnedSessionDeletesIt() {
        AuthToken token = new AuthToken(userWithPassword("hash"), "h", LocalDateTime.now().plusDays(1));
        when(authTokenRepository.findByIdAndUserId(3L, 7L)).thenReturn(Optional.of(token));

        service.revokeSession(7L, 3L);

        verify(authTokenRepository).delete(token);
    }

    @Test
    void revokingAnUnownedOrMissingSessionThrows() {
        when(authTokenRepository.findByIdAndUserId(3L, 7L)).thenReturn(Optional.empty());

        assertThrows(AuthenticationService.SessionNotFoundException.class, () -> service.revokeSession(7L, 3L));
    }
}
