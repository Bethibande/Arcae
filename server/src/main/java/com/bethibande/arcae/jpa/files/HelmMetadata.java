package com.bethibande.arcae.jpa.files;

import com.bethibande.arcae.jpa.artifact.ArtifactVersion;
import com.bethibande.arcae.repository.helm.HelmIndexEntry;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import org.hibernate.annotations.Type;

@Entity
public class HelmMetadata extends PanacheEntity {

    @ManyToOne(optional = false)
    public ArtifactVersion version;

    @Type(JsonBinaryType.class)
    @Column(nullable = false, columnDefinition = "jsonb")
    public HelmIndexEntry data;

}
