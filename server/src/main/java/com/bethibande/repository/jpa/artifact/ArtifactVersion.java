package com.bethibande.repository.jpa.artifact;

import com.bethibande.process.annotation.EntityDTO;
import com.bethibande.repository.jpa.files.StoredFile;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import io.quarkus.panache.common.Sort;
import jakarta.persistence.*;
import org.hibernate.annotations.Type;
import org.hibernate.search.engine.backend.types.Sortable;
import org.hibernate.search.mapper.pojo.automaticindexing.ReindexOnUpdate;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.*;

import java.time.Instant;
import java.util.List;

@Entity
@Indexed
@EntityDTO(excludeProperties = {"artifact", "files"}, name = "ArtifactVersionDTO")
@EntityDTO(excludeProperties = {"artifact", "details", "files"}, name = "ArtifactVersionDTOWithoutDetails")
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
    @Column(nullable = false, columnDefinition = "varchar(128)")
    public String version;

    @GenericField(sortable = Sortable.YES)
    @Column(nullable = false, columnDefinition = "timestamptz")
    public Instant created;

    @GenericField(sortable = Sortable.YES)
    @Column(nullable = false, columnDefinition = "timestamptz")
    public Instant updated;

    /**
     * Denotes the date after which the mirror may pull the version from the remote again.
     * This is useful for package managers where we need to eagerly pull the versions from the remote to update static tags such as "latest" properly
     */
    @Column(columnDefinition = "timestamptz")
    public Instant mirrorTTL;

    @Column(columnDefinition = "jsonb")
    @Type(JsonBinaryType.class)
    public ArtifactDetails details;

    @ManyToOne
    public StoredFile manifest;

    @ManyToMany
    @JoinTable(name = "ArtifactVersion_files")
    public List<StoredFile> files;

    public boolean isLocalArtifact() {
        return this.mirrorTTL == null;
    }

    public boolean mirrorTTLExpired(final Instant now) {
        return this.mirrorTTL != null && now.isAfter(this.mirrorTTL);
    }

}
