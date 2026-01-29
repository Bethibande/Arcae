package com.bethibande.repository.jpa.artifact;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import io.quarkus.panache.common.Sort;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import org.hibernate.search.mapper.pojo.automaticindexing.ReindexOnUpdate;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.*;

import java.time.Instant;

@Entity
@Indexed
public class ArtifactVersion extends PanacheEntity {

    public static Instant findMaxUpdated(final long repositoryId) {
        return ArtifactVersion.<ArtifactVersion>find("artifact.repository.id = ?1", Sort.descending("updated"), repositoryId)
                .firstResultOptional()
                .map(av -> av.updated)
                .orElse(null);
    }

    @ManyToOne(optional = false)
    @IndexedEmbedded(includePaths = "id")
    @IndexingDependency(reindexOnUpdate = ReindexOnUpdate.NO)
    public Artifact artifact;

    @FullTextField
    @Column(unique = true, nullable = false, columnDefinition = "varchar(128)")
    public String version;

    @GenericField
    @Column(nullable = false)
    public Instant created;

    @GenericField
    @Column(nullable = false)
    public Instant updated;

}
