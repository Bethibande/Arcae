package com.bethibande.repository.repository;

import com.bethibande.repository.jpa.artifact.Artifact;
import com.bethibande.repository.jpa.artifact.ArtifactVersion;
import com.bethibande.repository.jpa.repository.Repository;
import com.bethibande.repository.jpa.user.User;
import org.hibernate.search.mapper.orm.Search;
import org.hibernate.search.mapper.orm.session.SearchSession;

import java.sql.Timestamp;
import java.time.Instant;

public interface ManagedRepository {

    Repository getInfo();

    default boolean canView(final User user) {
        return getInfo().canView(user);
    }

    default boolean canWrite(final User user) {
        return getInfo().canWrite(user);
    }

    void delete(final User user, final ArtifactVersion version, final boolean skipAuth);

    void delete(final User user, final Artifact artifact, final boolean skipAuth);

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

}
