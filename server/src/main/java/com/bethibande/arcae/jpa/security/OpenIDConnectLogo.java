package com.bethibande.arcae.jpa.security;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;

import java.time.Duration;
import java.time.Instant;

@Entity
public class OpenIDConnectLogo extends PanacheEntity {

    public static final Duration MAX_LIFETIME = Duration.ofDays(30);

    @ManyToOne(optional = false)
    public OpenIDConnectProvider provider;

    @Column(nullable = false, columnDefinition = "timestamptz")
    public Instant createdAt;

    @Column(nullable = false, columnDefinition = "bytea")
    public byte[] data;

    @Column(nullable = false, columnDefinition = "varchar(255)")
    public String contentType;

    public boolean isExpired() {
        return Instant.now().isAfter(this.createdAt.plus(MAX_LIFETIME));
    }

}
