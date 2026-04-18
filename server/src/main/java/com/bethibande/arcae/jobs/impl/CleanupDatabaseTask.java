package com.bethibande.arcae.jobs.impl;

import com.bethibande.arcae.jobs.JobType;
import com.bethibande.arcae.jpa.security.OpenIDConnectLogo;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;

@ApplicationScoped
public class CleanupDatabaseTask implements JobTask<Object> {

    @Override
    public Class<Object> getConfigType() {
        return Object.class;
    }

    @Override
    public JobType getJobType() {
        return JobType.CLEAN_UP_DATABASE;
    }

    @Override
    public void run(final Object config) {
        QuarkusTransaction.requiringNew().run(() -> {
            final Instant now = Instant.now();
            final Instant maxLogoAge = now.minus(OpenIDConnectLogo.MAX_LIFETIME);

            OpenIDConnectLogo.delete("createdAt < ?1", maxLogoAge);
        });
    }
}
