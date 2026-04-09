package com.bethibande.arcae.jobs.impl;

import com.bethibande.arcae.jobs.JobType;
import com.bethibande.arcae.jpa.security.RefreshToken;
import com.bethibande.arcae.jpa.security.UserSession;
import com.bethibande.arcae.jpa.user.PasswordResetToken;
import com.bethibande.arcae.security.UserSessionService;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;

@ApplicationScoped
public class DeleteExpiredSessionsTask implements JobTask<Object> {

    @Override
    public Class<Object> getConfigType() {
        return Object.class;
    }

    @Override
    public JobType getJobType() {
        return JobType.DELETE_EXPIRED_SESSIONS;
    }

    @Override
    public void run(final Object config) {
        final Instant now = Instant.now();
        final Instant minSessionAge = now.minus(UserSessionService.SESSION_LIFETIME);
        final Instant minRefreshTokenAge = now.minus(UserSessionService.REFRESH_TOKEN_LIFETIME);

        QuarkusTransaction.requiringNew().run(() -> {
            UserSession.delete("created < ?1", minSessionAge);
            RefreshToken.delete("created < ?1", minRefreshTokenAge);
            PasswordResetToken.delete("expiration < ?1", now);
        });
    }
}
