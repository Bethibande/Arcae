package com.bethibande.repository.jpa.repository;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import org.hibernate.annotations.Type;
import org.hibernate.search.engine.backend.types.Sortable;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.GenericField;

@Entity
public class Repository extends PanacheEntityBase {

    @Id
    @GeneratedValue
    @GenericField(sortable = Sortable.YES)
    public Long id;

    @Column(length = 64, nullable = false, unique = true)
    public String name;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    public RepositoryType type;

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    public String settings;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    public RepositoryBackend backend;

}
