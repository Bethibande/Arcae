package com.bethibande.repository.jpa;

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
import java.util.Map;

@Entity
public class StoredFile extends PanacheEntity {

    @ManyToOne(optional = false)
    public Repository repository;

    @Column(nullable = false, columnDefinition = "varchar(1024)")
    public String key;

    @Column(nullable = false, columnDefinition = "timestamptz")
    public Instant created;

    @Column(nullable = false, columnDefinition = "timestamptz")
    public Instant updated;

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    public Map<String, String> hashes;

    public long usages() {
        final long versions = ArtifactVersion.count("files.id = ?", id);
        final long artifacts = Artifact.count("files.id = ?", id);

        return versions + artifacts;
    }

}
