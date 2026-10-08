package com.jobera.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.jobera.entity.AuthToken;
import com.jobera.entity.User;
import com.jobera.repository.AuthTokenRepository;
import com.jobera.repository.UserRepository;
import com.jobera.util.PasswordEncoder;
import com.jobera.util.TokenHasher;

@Service
public class AuthenticationService {

    private static final long TOKEN_VALIDITY_DAYS = 7;
    
    private final UserRepository userRepository;
    private final AuthTokenRepository authTokenRepository;
    private final ReferralService referralService;
    private final PasswordEncoder passwordEncoder;

    public AuthenticationService(UserRepository userRepository,
                               AuthTokenRepository authTokenRepository,
                               ReferralService referralService) {
        this.userRepository = userRepository;
        this.authTokenRepository = authTokenRepository;
        this.referralService = referralService;
        this.passwordEncoder = new PasswordEncoder();
    }

    public User registerUser(String email, String password, String fullName, String phone) {
        return registerUser(email, password, fullName, phone, null);
    }

    public User registerUser(String email, String password, String fullName, String phone, String referralCode) {
        // Check if user already exists
        if (userRepository.findByEmail(email).isPresent()) {
            throw new AuthenticationException("User with email " + email + " already exists");
        }

        // Create new user
        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setFullName(fullName);
        user.setPhone(phone);
        user.setIsActive(true);
        user.setRoleId(1L); // ROLE_USER
        user.setReferralCode(referralService.generateUniqueCode());

        User saved = userRepository.save(user);
        referralService.recordReferralIfValid(referralCode, saved);
        return saved;
    }
    
    public User loginUser(String email, String password) {
        Optional<User> userOptional = userRepository.findByEmail(email);
        
        if (userOptional.isEmpty()) {
            throw new AuthenticationException("Invalid email or password");
        }
        
        User user = userOptional.get();
        
        // Use primitive boolean check
        if (user.getIsActive() != null && !user.getIsActive()) {
            throw new AuthenticationException("Account is deactivated");
        }
        
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new AuthenticationException("Invalid email or password");
        }
        
        if (passwordEncoder.needsUpgrade(user.getPassword())) {
            user.setPassword(passwordEncoder.encode(password));
        }
        
        // Update last login
        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);
        
        return user;
    }
    
    public String generateAuthToken(User user) {
        return generateAuthToken(user, null, null);
    }

    public String generateAuthToken(User user, String userAgent, String ipAddress) {
        String token = TokenHasher.generate();
        LocalDateTime expiryDate = LocalDateTime.now().plusDays(TOKEN_VALIDITY_DAYS);

        AuthToken authToken = new AuthToken(user, TokenHasher.hash(token), expiryDate);
        authToken.setUserAgent(userAgent);
        authToken.setIpAddress(ipAddress);
        authTokenRepository.save(authToken);

        return token;
    }

    public Optional<User> validateToken(String token) {
        Optional<AuthToken> authTokenOptional = authTokenRepository.findByToken(TokenHasher.hash(token));
        
        if (authTokenOptional.isEmpty()) {
            return Optional.empty();
        }
        
        AuthToken authToken = authTokenOptional.get();
        
        if (authToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            authTokenRepository.delete(authToken);
            return Optional.empty();
        }

        authToken.setLastUsedAt(LocalDateTime.now());
        authTokenRepository.save(authToken);

        return Optional.of(authToken.getUser());
    }

    public void logout(String token) {
        authTokenRepository.deleteByToken(TokenHasher.hash(token));
    }

    public void logoutAllUserSessions(Long userId) {
        authTokenRepository.deleteByUserId(userId);
    }

    /** Every still-valid session for a user, most recently active first - the data behind the
     *  "active sessions" security page. */
    public List<AuthToken> listSessions(Long userId) {
        return authTokenRepository.findByUserIdAndExpiryDateAfterOrderByLastUsedAtDesc(userId, LocalDateTime.now());
    }

    public static class SessionNotFoundException extends RuntimeException {
        public SessionNotFoundException() {
            super("Session not found");
        }
    }

    /** Signs a single session out remotely - e.g. a lost phone or a shared computer - without
     *  touching the caller's own current session. */
    public void revokeSession(Long userId, Long sessionId) {
        AuthToken authToken = authTokenRepository.findByIdAndUserId(sessionId, userId)
            .orElseThrow(SessionNotFoundException::new);
        authTokenRepository.delete(authToken);
    }
    // Add this method to your existing AuthenticationService class
public boolean isUserRecruiter(Long userId) {
    Optional<User> user = userRepository.findById(userId);
    return user.isPresent() && user.get().getRoleId() == 3L; // ROLE_RECRUITER
}
// Add to AuthenticationService.java
public boolean isUserAdmin(Long userId) {
    Optional<User> user = userRepository.findById(userId);
    return user.isPresent() && user.get().getRoleId() == 2L; // ROLE_ADMIN
}

public Optional<User> findById(Long userId) {
    return userRepository.findById(userId);
}
    public static class AuthenticationException extends RuntimeException {
        public AuthenticationException(String message) {
            super(message);
        }
    }
}