package com.bethibande.arcae.jpa.files;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToOne;

import java.util.HashMap;
import java.util.Map;

@Entity
public class OCISubject extends PanacheEntity {

    @OneToOne(optional = false)
    public StoredFile source;

    @Column(nullable = false, columnDefinition = "varchar(255)")
    public String sourceDigest;

    @OneToOne
    public StoredFile subject;

    @Column(nullable = false, columnDefinition = "varchar(255)")
    public String namespace;

    @Column(nullable = false, columnDefinition = "varchar(255)")
    public String subjectDigest;

    @Column(nullable = false, columnDefinition = "varchar(255)")
    public String artifactType;

    @ElementCollection
    public Map<String, String> annotations = new HashMap<>();

}
