package com.bethibande.repository.jpa.files;

import com.bethibande.repository.jpa.artifact.Artifact;
import com.bethibande.repository.jpa.artifact.ArtifactVersion;
import com.bethibande.repository.jpa.repository.Repository;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Entity
public class StoredFile extends PanacheEntity {

    @SuppressWarnings("unchecked")
    public static List<StoredFile> findOrphanedFiles(final Repository repository, final Instant minAge, final int limit) {
        final List<Long> ids = (List<Long>) StoredFile.getEntityManager()
                .createNativeQuery("""
                        SELECT f.id FROM StoredFile f
                        WHERE f.created <= ?1
                          AND f.repository_id = ?2
                          AND NOT EXISTS (SELECT 1 FROM ArtifactVersion av WHERE av.manifest_id = f.id)
                          AND NOT EXISTS (SELECT 1 FROM ArtifactVersion_files avf WHERE avf.files_id = f.id)
                          AND NOT EXISTS (SELECT 1 FROM Artifact_files af WHERE af.files_id = f.id)
                        LIMIT ?3
                        """)
                .setParameter(1, minAge)
                .setParameter(2, limit)
                .setParameter(3, repository.id)
                .getResultList();

        return StoredFile.findByIds(ids);
    }

    @ManyToOne(optional = false)
    public Repository repository;

    @Column(nullable = false, columnDefinition = "varchar(1024)")
    public String key;

    @Column(nullable = false, columnDefinition = "timestamptz")
    public Instant created;

    @Column(nullable = false, columnDefinition = "timestamptz")
    public Instant updated;

    @Column
    public Long contentLength;

    @Column(columnDefinition = "varchar(255)")
    public String contentType;

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    public Map<String, String> hashes;

    public long usages() {
        final long manifests = ArtifactVersion.count("manifest = ?1", this);
        final long versions = ArtifactVersion.count("join files f on f.id = ?1", id);
        final long artifacts = Artifact.count("join files f on f.id = ?1", id);

        return versions + artifacts + manifests;
    }

    public void clearOwners() {
        ArtifactVersion.getEntityManager()
                .createNativeQuery("delete from artifactversion_files f where f.files_id = ?1")
                .setParameter(1, this.id)
                .executeUpdate();

        Artifact.getEntityManager()
                .createNativeQuery("delete from artifact_files f where f.files_id = ?1")
                .setParameter(1, this.id)
                .executeUpdate();

        ArtifactVersion.update("manifest = null where manifest = ?1", this);

        StoredFile.getEntityManager().flush();
    }

}
