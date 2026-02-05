package com.bethibande.repository.repository.cleanup;

import com.bethibande.repository.jpa.artifact.ArtifactVersion;
import com.bethibande.repository.jpa.repository.Repository;
import com.bethibande.repository.repository.ManagedRepository;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@RegisterForReflection
public record MaxAgeCleanupPolicy(
        boolean enabled,
        long time,
        ChronoUnit unit
) {

    public void cleanup(final Repository repository, final ManagedRepository managedRepository) {
        final Instant maxAge = Instant.now().minus(time, unit);
        ArtifactVersion.<ArtifactVersion>stream("artifact.repository = ?1 AND updated < ?2", repository, maxAge)
                .forEach(version -> managedRepository.delete(null, version, true));
    }

}
