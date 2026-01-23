package com.bethibande.repository.jpa.artifact;

import com.bethibande.repository.jpa.repository.Repository;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import org.hibernate.search.engine.backend.types.Sortable;
import org.hibernate.search.mapper.pojo.automaticindexing.ReindexOnUpdate;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.*;

@Entity
@Indexed
public class Artifact extends PanacheEntityBase {

    @Id
    @GeneratedValue
    @GenericField(sortable = Sortable.YES)
    public Long id;

    @ManyToOne(optional = false)
    @IndexedEmbedded(includePaths = "id")
    @IndexingDependency(reindexOnUpdate = ReindexOnUpdate.NO)
    public Repository repository;

    @FullTextField
    @Column(unique = true, nullable = false, columnDefinition = "varchar(512)")
    public String groupId;

    @FullTextField
    @Column(unique = true, nullable = false, columnDefinition = "varchar(128)")
    public String artifactId;

}
