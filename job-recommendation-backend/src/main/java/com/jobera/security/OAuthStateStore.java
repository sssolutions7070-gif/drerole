package com.jobera.security;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * The OAuth2 "state" parameter, tracked server-side: without this, an attacker could trick a
 * victim's browser into completing an OAuth callback the attacker initiated, linking the
 * victim's session to an account the attacker controls (a real, named class of OAuth
 * vulnerability - CSRF on the callback). A state is single-use and expires quickly; either one
 * closes the window it would otherwise leave open.
 */
@Component
public class OAuthStateStore {

    private static final long TTL_MILLIS = 10 * 60 * 1000L; // 10 minutes - long enough for a real login, short enough to matter

    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Long> issuedAt = new ConcurrentHashMap<>();

    public OAuthStateStore() {
        this(Clock.systemUTC());
    }

    OAuthStateStore(Clock clock) {
        this.clock = clock;
    }

    public String issue() {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        issuedAt.put(state, clock.millis());
        return state;
    }

    /** Single-use: valid exactly once, regardless of the outcome. */
    public boolean consume(String state) {
        if (state == null) {
            return false;
        }
        Long issued = issuedAt.remove(state);
        return issued != null && (clock.millis() - issued) <= TTL_MILLIS;
    }
}
