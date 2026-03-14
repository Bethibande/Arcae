package com.bethibande.repository.jpa.user;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;

import java.time.Instant;

@Entity
public class PasswordResetToken extends PanacheEntity {

    @ManyToOne(optional = false)
    public User user;

    @Column(nullable = false, unique = true, columnDefinition = "varchar(9)")
    public String token;

    @Column(nullable = false, columnDefinition = "timestamptz")
    public Instant expiration;

    public boolean isExpired(final Instant now) {
        return this.expiration.isBefore(now);
    }

}
