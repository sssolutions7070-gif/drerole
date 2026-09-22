package com.jobera.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.jobera.entity.AuthToken;
import com.jobera.entity.User;
import com.jobera.repository.AuthTokenRepository;
import com.jobera.repository.UserRepository;
import com.jobera.util.PasswordEncoder;

@Service
public class AuthenticationService {
    
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final long TOKEN_VALIDITY_DAYS = 7;
    
    private final UserRepository userRepository;
    private final AuthTokenRepository authTokenRepository;
    private final PasswordEncoder passwordEncoder;
    
    public AuthenticationService(UserRepository userRepository,
                               AuthTokenRepository authTokenRepository) {
        this.userRepository = userRepository;
        this.authTokenRepository = authTokenRepository;
        this.passwordEncoder = new PasswordEncoder();
    }
    
    public User registerUser(String email, String password, String fullName, String phone) {
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
        
        return userRepository.save(user);
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
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        LocalDateTime expiryDate = LocalDateTime.now().plusDays(TOKEN_VALIDITY_DAYS);
        
        AuthToken authToken = new AuthToken(user, hashToken(token), expiryDate);
        authTokenRepository.save(authToken);
        
        return token;
    }
    
    /** Tokens are stored as SHA-256 hex digests so a database leak does not expose usable sessions. */
    static String hashToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
    
    public Optional<User> validateToken(String token) {
        Optional<AuthToken> authTokenOptional = authTokenRepository.findByToken(hashToken(token));
        
        if (authTokenOptional.isEmpty()) {
            return Optional.empty();
        }
        
        AuthToken authToken = authTokenOptional.get();
        
        if (authToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            authTokenRepository.delete(authToken);
            return Optional.empty();
        }
        
        return Optional.of(authToken.getUser());
    }
    
    public void logout(String token) {
        authTokenRepository.deleteByToken(hashToken(token));
    }
    
    public void logoutAllUserSessions(Long userId) {
        authTokenRepository.deleteByUserId(userId);
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
    public static class AuthenticationException extends RuntimeException {
        public AuthenticationException(String message) {
            super(message);
        }
    }
}