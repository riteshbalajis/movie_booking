package com.movie_booking.web;

import com.movie_booking.model.UserRole;
import com.movie_booking.util.AppConfig;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side sessions, kept in memory and addressed by an opaque cookie token.
 *
 * <h2>Why the cookie holds only a token</h2>
 *
 * The browser never receives the user id or role - only a long random string that
 * means nothing on its own. Everything that matters is looked up here, server
 * side. If the cookie carried {@code userId=7&role=ADMIN}, anyone could edit it
 * and become an administrator; a signed token would fix that but needs key
 * management, which is more than this application needs.
 *
 * <p>Tokens come from {@link SecureRandom}, not {@code Math.random()}. A
 * predictable token is as good as a stolen password - an attacker who can guess
 * the next one is simply logged in as that user.
 *
 * <h2>Thread safety</h2>
 *
 * Every HTTP request runs on a pool thread, so this map is touched concurrently
 * all the time. {@link ConcurrentHashMap} handles that without a global lock;
 * synchronising the whole store would make every request in the server queue on
 * one monitor.
 *
 * <p>Being in memory, sessions do not survive a restart - everyone signs in
 * again. For this project that is the right trade: no extra table, no cleanup
 * job, and a restart is not a normal event. A clustered deployment would need
 * these in a shared store instead, for the same reason application-level locks
 * would not work there.
 */
public final class SessionStore {

    private static final long TIMEOUT_MINUTES =
            AppConfig.getLong("session.timeoutMinutes", 120L);

    private static final int TOKEN_BYTES = 32;

    private final Map<String, Session> sessions = new ConcurrentHashMap<String, Session>();
    private final SecureRandom random = new SecureRandom();

    /** One signed-in user. Immutable apart from the sliding expiry. */
    public static final class Session {

        private final int userId;
        private final String name;
        private final String email;
        private final UserRole role;
        private volatile long expiresAtMillis;

        private Session(int userId, String name, String email, UserRole role, long expiresAt) {
            this.userId = userId;
            this.name = name;
            this.email = email;
            this.role = role;
            this.expiresAtMillis = expiresAt;
        }

        public int getUserId() { return userId; }
        public String getName() { return name; }
        public String getEmail() { return email; }
        public UserRole getRole() { return role; }
        public boolean isAdmin() { return role == UserRole.ADMIN; }

        boolean isExpired(long now) {
            return now > expiresAtMillis;
        }
    }

    /** @return the token to put in the cookie */
    public String create(int userId, String name, String email, UserRole role) {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        sessions.put(token, new Session(userId, name, email, role, expiryFromNow()));
        return token;
    }

    /**
     * @return the session, or {@code null} if the token is unknown or stale
     */
    public Session lookup(String token) {
        if (token == null) {
            return null;
        }

        Session session = sessions.get(token);
        if (session == null) {
            return null;
        }
        if (session.isExpired(System.currentTimeMillis())) {
            sessions.remove(token);
            return null;
        }

        // Sliding expiry: an active user is not signed out mid-booking.
        session.expiresAtMillis = expiryFromNow();
        return session;
    }

    public void invalidate(String token) {
        if (token != null) {
            sessions.remove(token);
        }
    }

    /**
     * Drops sessions that have timed out.
     *
     * <p>Expired entries are already refused by {@link #lookup}, so this is purely
     * to stop the map growing without bound in a long-running process - a user who
     * closes the tab never comes back to have their entry cleaned up lazily.
     */
    public int purgeExpired() {
        long now = System.currentTimeMillis();
        int removed = 0;

        for (Iterator<Map.Entry<String, Session>> it = sessions.entrySet().iterator();
                it.hasNext();) {
            if (it.next().getValue().isExpired(now)) {
                it.remove();
                removed++;
            }
        }
        return removed;
    }

    public int activeSessionCount() {
        return sessions.size();
    }

    private long expiryFromNow() {
        return System.currentTimeMillis() + TIMEOUT_MINUTES * 60_000L;
    }
}
