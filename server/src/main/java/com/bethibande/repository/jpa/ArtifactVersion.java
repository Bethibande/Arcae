package com.bethibande.repository.jpa;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import org.hibernate.search.mapper.pojo.automaticindexing.ReindexOnUpdate;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.*;

import java.time.Instant;

@Entity
@Indexed
public class ArtifactVersion extends PanacheEntity {

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
