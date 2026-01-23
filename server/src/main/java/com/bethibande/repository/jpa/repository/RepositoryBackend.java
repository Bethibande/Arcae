package com.bethibande.repository.jpa.repository;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import org.hibernate.annotations.Type;

@Entity
public class RepositoryBackend extends PanacheEntity {

    @Column(unique = true, nullable = false)
    public String name;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    public RepositoryBackendType type;

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    public String settings;
}
