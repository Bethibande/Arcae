package com.bethibande.repository.jpa.security;

import com.bethibande.repository.jpa.user.User;
import com.bethibande.repository.security.UserSessionService;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;

import java.time.Instant;
import java.util.List;

@Entity
public class RefreshToken extends PanacheEntity {

    @ManyToOne(optional = false)
    public User user;

    @Column(nullable = false, unique = true, columnDefinition = "varchar(512)")
    public String token;

    @Column(nullable = false, columnDefinition = "timestamptz")
    public Instant created;

    @OneToMany(mappedBy = "refreshToken")
    public List<UserSession> sessions;

    public Instant expiresAfter() {
        return this.created.plus(UserSessionService.REFRESH_TOKEN_LIFETIME);
    }

    public boolean isExpired(final Instant now) {
        return now.isAfter(this.expiresAfter());
    }

}
