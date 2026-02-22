package com.bethibande.repository.jobs.impl;

import com.bethibande.repository.jobs.JobType;
import com.bethibande.repository.jpa.security.UserSession;
import com.bethibande.repository.security.UserSessionService;
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
        final Instant minAge = now.minus(UserSessionService.SESSION_LIFETIME);

        QuarkusTransaction.requiringNew().run(() -> {
            UserSession.delete("created < ?1", minAge);
        });
    }
}
