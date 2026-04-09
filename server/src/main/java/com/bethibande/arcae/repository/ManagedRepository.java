package com.bethibande.arcae.repository;

import com.bethibande.arcae.repository.maven.MavenRepository;
import com.bethibande.arcae.jpa.artifact.Artifact;
import com.bethibande.arcae.jpa.artifact.ArtifactVersion;
import com.bethibande.arcae.jpa.files.StoredFile;
import com.bethibande.arcae.jpa.repository.Repository;
import com.bethibande.arcae.repository.oci.OCIRepository;
import com.bethibande.arcae.repository.security.AuthContext;
import org.hibernate.search.mapper.orm.Search;
import org.hibernate.search.mapper.orm.session.SearchSession;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;

/**
 * A ManagedRepository is an implementation of a repository for a specific package manager backed by a repository entity describing its features/settings.
 *
 * @see RepositoryUpdatedNotifier
 * @see MavenRepository
 * @see OCIRepository
 */
public interface ManagedRepository {

    Map<String, Object> generateMetadata();

    Repository getInfo();

    default boolean canView(final AuthContext auth) {
        return getInfo().canView(auth);
    }

    default boolean canWrite(final AuthContext auth) {
        return getInfo().canWrite(auth);
    }

    void delete(final StoredFile file);

    void delete(final AuthContext auth, final ArtifactVersion version);

    void delete(final AuthContext auth, final Artifact artifact);

    default Artifact getOrCreateArtifact(final String groupId, final String artifactId, final Instant now) {
        final long id = (Long) Artifact.getEntityManager()
                .createNativeQuery(
                        """
                                INSERT INTO Artifact (id, groupId, artifactId, repository_id, lastUpdated)
                                VALUES (nextval('artifact_seq'), ?, ?, ?, ?)
                                ON CONFLICT (groupId, artifactId, repository_id)
                                DO UPDATE SET lastUpdated = EXCLUDED.lastUpdated
                                RETURNING id""")
                .setParameter(1, groupId)
                .setParameter(2, artifactId)
                .setParameter(3, getInfo().id)
                .setParameter(4, Timestamp.from(now))
                .getSingleResult();

        final SearchSession searchSession = Search.session(Artifact.getEntityManager());
        final Artifact artifact = Artifact.findById(id);

        searchSession.indexingPlan().addOrUpdate(artifact);

        return artifact;
    }

    default ArtifactVersion getOrCreateArtifactVersion(final Artifact artifact, final String version, final Instant now) {
        final long id = (Long) ArtifactVersion.getEntityManager()
                .createNativeQuery(
                        """
                                INSERT INTO ArtifactVersion (id, artifact_id, version, created, updated)
                                VALUES (nextval('artifactversion_seq'), ?, ?, ?, ?)
                                ON CONFLICT (artifact_id, version)
                                DO UPDATE SET updated = EXCLUDED.updated
                                RETURNING id""")
                .setParameter(1, artifact.id)
                .setParameter(2, version)
                .setParameter(3, Timestamp.from(now))
                .setParameter(4, Timestamp.from(now))
                .getSingleResult();

        final SearchSession searchSession = Search.session(ArtifactVersion.getEntityManager());
        final ArtifactVersion artifactVersion = ArtifactVersion.findById(id);

        searchSession.indexingPlan().addOrUpdate(artifactVersion);

        return artifactVersion;
    }

    void close();

}
