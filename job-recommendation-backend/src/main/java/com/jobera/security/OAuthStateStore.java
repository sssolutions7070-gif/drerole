package com.jobera.security;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
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

    /** issuedAt, and (only for a user-bound state - see issueForUser) the user it belongs to. */
    private record Entry(long issuedAt, Long userId) {}

    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Entry> issued = new ConcurrentHashMap<>();

    public OAuthStateStore() {
        this(Clock.systemUTC());
    }

    OAuthStateStore(Clock clock) {
        this.clock = clock;
    }

    public String issue() {
        return issue(null);
    }

    /** For a flow that starts from an already-authenticated page (e.g. "connect my calendar")
     *  rather than a login button: the callback has no session to read the user from (Google
     *  redirects the bare browser back, with no Authorization header), so the user who started
     *  the flow is threaded through via the state itself. */
    public String issueForUser(Long userId) {
        return issue(userId);
    }

    private String issue(Long userId) {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        issued.put(state, new Entry(clock.millis(), userId));
        return state;
    }

    /** Single-use: valid exactly once, regardless of the outcome. */
    public boolean consume(String state) {
        if (state == null) {
            return false;
        }
        Entry entry = issued.remove(state);
        return entry != null && (clock.millis() - entry.issuedAt()) <= TTL_MILLIS;
    }

    /** Single-use, like consume() - but for a state issued by issueForUser(), returning the user
     *  it was bound to instead of a plain boolean. */
    public Optional<Long> consumeUserId(String state) {
        if (state == null) {
            return Optional.empty();
        }
        Entry entry = issued.remove(state);
        if (entry == null || (clock.millis() - entry.issuedAt()) > TTL_MILLIS || entry.userId() == null) {
            return Optional.empty();
        }
        return Optional.of(entry.userId());
    }
}
