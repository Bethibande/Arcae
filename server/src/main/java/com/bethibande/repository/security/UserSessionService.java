package com.bethibande.repository.security;

import com.bethibande.repository.jpa.security.UserSession;
import com.bethibande.repository.jpa.user.User;
import io.quarkus.cache.CacheInvalidate;
import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.wildfly.security.util.PasswordUtil;

import java.time.Duration;
import java.time.Instant;

@ApplicationScoped
public class UserSessionService {

    // TODO: Config
    public static final Duration SESSION_LIFETIME = Duration.ofDays(7);

    @Transactional
    @CacheResult(cacheName = "user-sessions")
    public UserSession getSessionByToken(final String token) {
        return UserSession.find("token = ?1", token).firstResult();
    }

    @Transactional
    @CacheInvalidate(cacheName = "user-sessions")
    public void invalidateSession(final String token) {
        UserSession.delete("token = ?1", token);
    }

    public boolean isValid(final UserSession session) {
        final Instant now = Instant.now();
        return session != null && now.isBefore(session.expiresAfter());
    }

    @Transactional
    public UserSession createSessionForUser(final User user) {
        final UserSession session = new UserSession();
        session.user = user;
        session.created = Instant.now();
        session.token = PasswordUtil.generateSecureRandomString(256);
        session.persist();

        return session;
    }

}
