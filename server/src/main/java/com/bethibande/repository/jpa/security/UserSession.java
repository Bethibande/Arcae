package com.bethibande.repository.jpa.security;

import com.bethibande.repository.jpa.user.User;
import com.bethibande.repository.security.UserSessionService;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;

import java.time.Instant;

@Entity
public class UserSession extends PanacheEntity {

    @Column(unique = true, nullable = false, columnDefinition = "varchar(512)")
    public String token;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    public User user;

    @Column(nullable = false, columnDefinition = "varchar(512)")
    public String address;

    @Column(nullable = false, columnDefinition = "timestamptz")
    public Instant created;

    public Instant expiresAfter() {
        return this.created.plus(UserSessionService.SESSION_LIFETIME);
    }

}
