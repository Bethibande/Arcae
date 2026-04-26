package com.bethibande.arcae.jpa.repository;

import com.bethibande.process.annotation.EntityDTO;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;

@Entity
@EntityDTO
@EntityDTO(excludeProperties = "id")
public class S3RepositoryBackend extends PanacheEntity {

    @Column(nullable = false, columnDefinition = "VARCHAR(255)")
    public String name;

    @Column(nullable = false, columnDefinition = "VARCHAR(512)")
    public String uri;

    @Column(nullable = false, columnDefinition = "VARCHAR(255)")
    public String bucket;

    @Column(nullable = false, columnDefinition = "VARCHAR(255)")
    public String region;

    @Column(nullable = false, columnDefinition = "VARCHAR(1024)")
    public String accessKey;

    @Column(nullable = false, columnDefinition = "VARCHAR(1024)")
    public String secretKey;

}
