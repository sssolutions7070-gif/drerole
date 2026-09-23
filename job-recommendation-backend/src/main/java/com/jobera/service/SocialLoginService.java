package com.jobera.service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.jobera.entity.User;
import com.jobera.repository.UserRepository;
import com.jobera.service.OAuthService.OAuthException;
import com.jobera.service.OAuthService.OAuthUserInfo;
import com.jobera.util.PasswordEncoder;

/** Turns a verified OAuth identity into a local account: logs into an existing one with a
 *  matching email, or creates a new one. Kept separate from OAuthService, which only knows how
 *  to talk to the providers themselves. */
@Service
public class SocialLoginService {

    private final UserRepository userRepository;
    private final ReferralService referralService;
    private final PasswordEncoder passwordEncoder = new PasswordEncoder();
    private final SecureRandom random = new SecureRandom();

    public SocialLoginService(UserRepository userRepository, ReferralService referralService) {
        this.userRepository = userRepository;
        this.referralService = referralService;
    }

    public User findOrCreateUser(String provider, OAuthUserInfo info) {
        if (info.email() == null || info.email().isBlank()) {
            throw new OAuthException("Your " + provider + " account did not share an email address");
        }
        if (!info.emailVerified()) {
            throw new OAuthException("Your " + provider + " email address is not verified");
        }

        Optional<User> existing = userRepository.findByEmailIgnoreCase(info.email());
        if (existing.isPresent()) {
            User user = existing.get();
            if (user.getIsActive() != null && !user.getIsActive()) {
                throw new OAuthException("This account has been deactivated");
            }
            // First sign-in through this provider for an account that already exists (created
            // by normal registration, or by a different provider) - link it, don't duplicate it.
            if (user.getOauthProvider() == null) {
                user.setOauthProvider(provider);
                user.setOauthProviderId(info.providerId());
                userRepository.save(user);
            }
            return user;
        }

        User user = new User();
        user.setEmail(info.email());
        user.setFullName(info.fullName());
        user.setIsActive(true);
        user.setRoleId(1L); // ROLE_USER - same default as normal registration
        // A random secret nobody is ever told, so this account can never be reached by password
        // login - only through the OAuth provider that created it.
        user.setPassword(passwordEncoder.encode(randomOpaqueSecret()));
        user.setOauthProvider(provider);
        user.setOauthProviderId(info.providerId());
        user.setReferralCode(referralService.generateUniqueCode());

        return userRepository.save(user);
    }

    private String randomOpaqueSecret() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
