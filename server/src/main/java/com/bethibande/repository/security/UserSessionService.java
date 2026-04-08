package com.bethibande.repository.security;

import com.bethibande.repository.cache.DistributedCacheRegistry;
import com.bethibande.repository.jpa.security.RefreshToken;
import com.bethibande.repository.jpa.security.UserSession;
import com.bethibande.repository.jpa.user.User;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.quarkus.cache.CacheInvalidate;
import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.wildfly.security.util.PasswordUtil;

import java.time.Duration;
import java.time.Instant;

@ApplicationScoped
public class UserSessionService {

    public static final String USER_SESSION_CACHE = "user-sessions";

    // TODO: Config
    public static final Duration SESSION_LIFETIME = Duration.ofHours(1);
    public static final Duration REFRESH_TOKEN_LIFETIME = Duration.ofDays(7);

    private final Cache<String, UserSession> userSessionCache = Caffeine.newBuilder()
            .expireAfterWrite(SESSION_LIFETIME)
            .maximumSize(1_000)
            .build();

    private final DistributedCacheRegistry cacheRegistry;

    public UserSessionService(final DistributedCacheRegistry cacheRegistry) {
        this.cacheRegistry = cacheRegistry;

        cacheRegistry.register(USER_SESSION_CACHE, this.userSessionCache);
    }

    @Transactional
    public UserSession getSessionByToken(final String token) {
        return this.userSessionCache.get(token, _ -> UserSession.find("token = ?1", token).firstResult());
    }

    public void updateSession(final UserSession session) {
        this.cacheRegistry.invalidateAll(USER_SESSION_CACHE, session.token);
    }

    @Transactional
    public void invalidateSession(final String token) {
        final UserSession session = this.getSessionByToken(token);
        if (session != null) invalidateSession(session);
    }

    @Transactional
    public void invalidateSession(final UserSession session) {
        UserSession.deleteById(session.id);
        RefreshToken.deleteById(session.refreshToken.id);

        this.cacheRegistry.invalidateAll(USER_SESSION_CACHE, session.token);
    }

    public boolean isValid(final UserSession session) {
        final Instant now = Instant.now();
        return session != null && now.isBefore(session.expiresAfter());
    }

    @Transactional
    public UserSession createSessionForUser(final User user, final String remoteAddress) {
        final UserSession session = new UserSession();
        session.user = user;
        session.created = Instant.now();
        session.address = remoteAddress;
        session.token = PasswordUtil.generateSecureRandomString(256);
        session.refreshToken = this.createRefreshTokenForUser(user);
        session.persist();

        return session;
    }

    @Transactional
    public RefreshToken createRefreshTokenForUser(final User user) {
        final RefreshToken token = new RefreshToken();
        token.user = user;
        token.created = Instant.now();
        token.token = PasswordUtil.generateSecureRandomString(512);
        token.persist();

        return token;
    }

}
