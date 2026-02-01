package com.bethibande.repository.jpa.artifact;

import com.bethibande.process.annotation.EntityDTO;
import com.bethibande.process.annotation.VirtualDTOField;
import com.bethibande.repository.jpa.StoredFile;
import com.bethibande.repository.jpa.repository.Repository;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.panache.common.Sort;
import jakarta.persistence.*;
import org.hibernate.search.engine.backend.types.Searchable;
import org.hibernate.search.engine.backend.types.Sortable;
import org.hibernate.search.mapper.pojo.automaticindexing.ReindexOnUpdate;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.*;

import java.time.Instant;
import java.util.List;

@Entity
@Indexed
@EntityDTO(excludeProperties = "files", name = "ArtifactDTO")
@EntityDTO(excludeProperties = {"id", "files"}, name = "ArtifactDTOWithoutId")
public class Artifact extends PanacheEntityBase {

    @Id
    @GeneratedValue
    @GenericField(sortable = Sortable.YES)
    public Long id;

    @ManyToOne(optional = false)
    @IndexedEmbedded(includePaths = "id")
    @IndexingDependency(reindexOnUpdate = ReindexOnUpdate.NO)
    public Repository repository;

    @FullTextField(analyzer = "artifact_path")
    @Column(nullable = false, columnDefinition = "varchar(512)")
    public String groupId;

    @FullTextField(analyzer = "artifact_path")
    @Column(nullable = false, columnDefinition = "varchar(128)")
    public String artifactId;

    @Column(nullable = false, columnDefinition = "timestamptz")
    @GenericField(sortable = Sortable.YES, searchable = Searchable.YES)
    public Instant lastUpdated;

    @ManyToMany
    @JoinTable(name = "Artifact_files")
    public List<StoredFile> files;

    @VirtualDTOField
    public String latestVersion() {
        return ArtifactVersion.<ArtifactVersion>find("artifact.id = ?1", Sort.descending("updated"), id)
                .firstResultOptional()
                .map(av -> av.version)
                .orElse(null);
    }

    public long countVersions() {
        return ArtifactVersion.count("artifact.id = ?1", id);
    }

}
