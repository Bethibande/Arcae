package com.bethibande.repository.jpa;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.Type;

@Entity
public class Repository extends PanacheEntity {

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
