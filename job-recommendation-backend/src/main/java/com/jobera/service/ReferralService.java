package com.jobera.service;

import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.jobera.entity.Referral;
import com.jobera.entity.User;
import com.jobera.repository.ReferralRepository;
import com.jobera.repository.UserRepository;

/**
 * Every user gets a unique referral code at signup; anyone who registers with it recorded as
 * having been referred. There's no reward/credit mechanic here - this app has no wallet, points
 * or discount system to hook a reward into, and inventing one that doesn't connect to anything
 * real would be worse than not having it. What this delivers is the honest, useful part: knowing
 * your referral code, your link, and who you've actually brought in.
 */
@Service
public class ReferralService {

    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no 0/O/1/I
    private static final int CODE_LENGTH = 8;
    private static final int MAX_GENERATION_ATTEMPTS = 20;

    private final UserRepository userRepository;
    private final ReferralRepository referralRepository;
    private final SecureRandom random = new SecureRandom();

    public ReferralService(UserRepository userRepository, ReferralRepository referralRepository) {
        this.userRepository = userRepository;
        this.referralRepository = referralRepository;
    }

    /** Called once, at signup, for every new user - referred or not. */
    public String generateUniqueCode() {
        for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
            String candidate = randomCode();
            if (!userRepository.existsByReferralCode(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Failed to generate a unique referral code after "
            + MAX_GENERATION_ATTEMPTS + " attempts");
    }

    private String randomCode() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
        }
        return code.toString();
    }

    /**
     * Best-effort: an unknown or self-referral code is silently ignored rather than failing
     * registration - a typo'd or expired referral link shouldn't be able to block someone from
     * creating an account.
     */
    public void recordReferralIfValid(String referralCode, User newUser) {
        if (referralCode == null || referralCode.isBlank()) {
            return;
        }
        userRepository.findByReferralCode(referralCode.trim().toUpperCase())
            .filter(referrer -> !referrer.getId().equals(newUser.getId()))
            .ifPresent(referrer -> referralRepository.save(new Referral(referrer, newUser)));
    }

    public Map<String, Object> getReferralStats(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        // Backfills a code for accounts created before this feature existed, rather than a bulk
        // migration - the first time someone checks their referral stats, they get one.
        if (user.getReferralCode() == null || user.getReferralCode().isBlank()) {
            user.setReferralCode(generateUniqueCode());
            userRepository.save(user);
        }

        List<Referral> referrals = referralRepository.findByReferrerIdOrderByCreatedAtDesc(userId);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("referralCode", user.getReferralCode());
        stats.put("totalReferrals", referrals.size());
        stats.put("referrals", referrals.stream().map(this::toView).toList());
        return stats;
    }

    private Map<String, Object> toView(Referral referral) {
        Map<String, Object> view = new LinkedHashMap<>();
        User referred = referral.getReferredUser();
        // First name only, not the full name or email - a referrer can see that someone signed
        // up through their link without getting a contact list of the people they referred.
        String firstName = referred.getFullName() != null && !referred.getFullName().isBlank()
            ? referred.getFullName().split("\\s+")[0]
            : "Someone";
        view.put("name", firstName);
        view.put("joinedAt", referral.getCreatedAt());
        return view;
    }
}
