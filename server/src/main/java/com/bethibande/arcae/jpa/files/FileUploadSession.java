package com.bethibande.arcae.jpa.files;

import com.bethibande.arcae.jpa.repository.Repository;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;

import java.time.Instant;

@Entity
public class FileUploadSession extends PanacheEntity {

    @ManyToOne(optional = false)
    public Repository repository;

    @Column(nullable = false, columnDefinition = "varchar(1024)")
    public String fileKey;

    @Column(nullable = false, unique = true, columnDefinition = "varchar(256)")
    public String uploadSessionId;

    @Column(columnDefinition = "varchar(1024)")
    public String hashingState;

    @Column(nullable = false, columnDefinition = "timestamptz")
    public Instant createdAt;

}
