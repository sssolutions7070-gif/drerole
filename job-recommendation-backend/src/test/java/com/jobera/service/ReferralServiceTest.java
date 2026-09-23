package com.jobera.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jobera.entity.Referral;
import com.jobera.entity.User;
import com.jobera.repository.ReferralRepository;
import com.jobera.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class ReferralServiceTest {

    @Mock UserRepository userRepository;
    @Mock ReferralRepository referralRepository;

    ReferralService service;

    @BeforeEach
    void setUp() {
        service = new ReferralService(userRepository, referralRepository);
    }

    @Test
    void generatedCodesAreUniqueAgainstExistingOnes() {
        when(userRepository.existsByReferralCode(anyString())).thenReturn(false);

        String code = service.generateUniqueCode();

        assertNotNull(code);
        assertEquals(8, code.length());
    }

    @Test
    void retriesOnCollisionUntilAUniqueCodeIsFound() {
        when(userRepository.existsByReferralCode(anyString())).thenReturn(true, true, false);

        String code = service.generateUniqueCode();

        assertNotNull(code);
        verify(userRepository, org.mockito.Mockito.times(3)).existsByReferralCode(anyString());
    }

    @Test
    void recordingAValidReferralCreatesTheLink() {
        User referrer = new User("referrer@example.com", "hash", "Referrer");
        referrer.setId(1L);
        User newUser = new User("new@example.com", "hash", "New User");
        newUser.setId(2L);
        when(userRepository.findByReferralCode("ABCD1234")).thenReturn(Optional.of(referrer));

        service.recordReferralIfValid("abcd1234", newUser);

        verify(referralRepository).save(any(Referral.class));
    }

    @Test
    void anUnknownReferralCodeIsSilentlyIgnored() {
        User newUser = new User("new@example.com", "hash", "New User");
        newUser.setId(2L);
        when(userRepository.findByReferralCode("NOTREAL1")).thenReturn(Optional.empty());

        service.recordReferralIfValid("NOTREAL1", newUser);

        verify(referralRepository, never()).save(any());
    }

    @Test
    void aBlankOrNullReferralCodeIsIgnoredWithoutLookingAnythingUp() {
        User newUser = new User("new@example.com", "hash", "New User");
        newUser.setId(2L);

        service.recordReferralIfValid(null, newUser);
        service.recordReferralIfValid("  ", newUser);

        verify(userRepository, never()).findByReferralCode(anyString());
        verify(referralRepository, never()).save(any());
    }

    @Test
    void aUserCannotReferThemselves() {
        User user = new User("self@example.com", "hash", "Self Referrer");
        user.setId(1L);
        when(userRepository.findByReferralCode("SELFCODE")).thenReturn(Optional.of(user));

        service.recordReferralIfValid("SELFCODE", user);

        verify(referralRepository, never()).save(any());
    }

    @Test
    void statsIncludeTheCodeAndEveryoneReferred() {
        User user = new User("referrer@example.com", "hash", "Referrer");
        user.setId(1L);
        user.setReferralCode("ABCD1234");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        User referred = new User("friend@example.com", "hash", "Friend Person");
        referred.setId(2L);
        Referral referral = new Referral(user, referred);
        when(referralRepository.findByReferrerIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(referral));

        Map<String, Object> stats = service.getReferralStats(1L);

        assertEquals("ABCD1234", stats.get("referralCode"));
        assertEquals(1, stats.get("totalReferrals"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> referrals = (List<Map<String, Object>>) stats.get("referrals");
        assertEquals("Friend", referrals.get(0).get("name"));
    }

    @Test
    void backfillsAMissingReferralCodeForAnOlderAccount() {
        User user = new User("old@example.com", "hash", "Old User");
        user.setId(1L);
        user.setReferralCode(null);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.existsByReferralCode(anyString())).thenReturn(false);
        when(referralRepository.findByReferrerIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());

        Map<String, Object> stats = service.getReferralStats(1L);

        assertNotNull(stats.get("referralCode"));
        assertTrue(user.getReferralCode() != null);
        verify(userRepository).save(user);
    }
}
