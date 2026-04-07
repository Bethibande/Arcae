package com.bethibande.repository.repository.helm;

import com.bethibande.repository.jpa.artifact.Artifact;
import com.bethibande.repository.jpa.artifact.ArtifactVersion;
import com.bethibande.repository.jpa.files.HelmMetadata;
import com.bethibande.repository.jpa.repository.Repository;
import com.bethibande.repository.repository.RepositoryApplicationContext;
import com.bethibande.repository.repository.oci.OCIRepository;
import com.bethibande.repository.repository.security.AuthContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.quarkus.security.UnauthorizedException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HelmRepository extends OCIRepository {

    public HelmRepository(final Repository info,
                          final RepositoryApplicationContext ctx) throws JsonProcessingException {
        super(info, ctx);
        super.index = new HelmChartIndex(this, ctx.objectMapper());
    }

    @Override
    protected String[] getRoutedSubpaths() {
        return new String[] {"v2", "repo"};
    }

    public Map<String, List<HelmIndexEntry>> getIndex(final AuthContext auth,
                                                      final String namespace,
                                                      final String urlTemplate) {
        if (!canView(auth)) throw new UnauthorizedException();

        final Map<String, List<HelmIndexEntry>> index = new HashMap<>();
        final List<Artifact> artifacts = new ArrayList<>();
        artifacts.add(super.getArtifact(auth, namespace));
        artifacts.addAll(super.getArtifacts(auth, namespace));

        for (int i = 0; i < artifacts.size(); i++) {
            final Artifact artifact = artifacts.get(i);
            if (artifact == null) continue;

            final List<HelmMetadata> versions = HelmMetadata.list("version.artifact = ?1", artifact);

            index.put(artifact.artifactId, versions.stream()
                    .map(md -> md.data)
                            .map(data -> new HelmIndexEntry(
                                    data.created(),
                                    data.description(),
                                    data.digest(),
                                    data.home(),
                                    data.name(),
                                    data.sources(),
                                    List.of(urlTemplate.formatted(artifact.groupId + "/" + artifact.artifactId, data.digest(), artifact.artifactId + "-" + data.version())),
                                    data.version()
                            ))
                    .toList());
        }

        return index;
    }

    @Override
    public void delete(final AuthContext auth, final ArtifactVersion version) {
        HelmMetadata.delete("version = ?1", version);
        super.delete(auth, version);
    }
}
