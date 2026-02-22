package com.bethibande.repository.repository.cleanup;

import com.bethibande.repository.jpa.artifact.ArtifactVersion;
import com.bethibande.repository.jpa.repository.Repository;
import com.bethibande.repository.repository.ManagedRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.narayana.jta.TransactionSemantics;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@RegisterForReflection
public record MaxAgeCleanupPolicy(
        boolean enabled,
        long time,
        ChronoUnit unit
) {

    public void cleanup(final Repository repository, final ManagedRepository managedRepository) {
        final Instant maxAge = Instant.now().minus(time, unit);

        QuarkusTransaction.requiringNew().run(() -> {
            final List<ArtifactVersion> versions = ArtifactVersion.find("artifact.repository = ?1 AND updated < ?2", repository, maxAge)
                    .page(0, 50)
                    .list();

            for (int i = 0; i < versions.size(); i++) {
                final ArtifactVersion version = versions.get(i);
                managedRepository.delete(null, version, true);
            }
        });
    }

}
