package com.jobera.config;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.jobera.repository.AuthTokenRepository;

@Component
@EnableScheduling
public class TokenCleanup {

    private static final Logger log = LoggerFactory.getLogger(TokenCleanup.class);

    private final AuthTokenRepository authTokenRepository;

    public TokenCleanup(AuthTokenRepository authTokenRepository) {
        this.authTokenRepository = authTokenRepository;
    }

    /** Sessions issued before tokens were hashed are stored in plain text, so they are invalidated once. */
    @EventListener(ApplicationReadyEvent.class)
    public void removeLegacyPlainTextTokens() {
        int removed = authTokenRepository.deleteUnhashed();
        if (removed > 0) {
            log.info("Invalidated {} legacy plain-text sessions; those users must sign in again", removed);
        }
    }

    @Scheduled(fixedDelay = 3_600_000, initialDelay = 60_000)
    public void removeExpiredTokens() {
        int removed = authTokenRepository.deleteExpired(LocalDateTime.now());
        if (removed > 0) {
            log.info("Removed {} expired sessions", removed);
        }
    }
}
