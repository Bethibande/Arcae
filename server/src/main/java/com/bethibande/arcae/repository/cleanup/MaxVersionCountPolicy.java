package com.bethibande.arcae.repository.cleanup;

import com.bethibande.arcae.jpa.artifact.Artifact;
import com.bethibande.arcae.jpa.artifact.ArtifactVersion;
import com.bethibande.arcae.repository.ManagedRepository;
import com.bethibande.arcae.repository.security.AuthContext;
import io.quarkus.panache.common.Sort;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.validation.constraints.Min;

import java.util.List;

@RegisterForReflection
public record MaxVersionCountPolicy(
        boolean enabled,
        @Min(0) int maxVersions
) {

    public void cleanup(final Artifact artifact, final ManagedRepository repository) {
        final int versions = (int) ArtifactVersion.count("artifact = ?1", artifact);

        if (versions > maxVersions()) {
            final int toDelete = versions - maxVersions();
            final List<ArtifactVersion> delete = ArtifactVersion.findAll(Sort.ascending("updated"))
                    .page(0, toDelete)
                    .list();
            for (int i = 0; i < delete.size(); i++) {
                final ArtifactVersion version = delete.get(i);
                repository.delete(AuthContext.ofSystem(), version);
            }
        }
    }

}
