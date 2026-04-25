package com.bethibande.arcae.jpa.user;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import org.wildfly.security.util.PasswordUtil;

import java.time.Duration;
import java.time.Instant;

@Entity
public class TwoFASession extends PanacheEntity {

    public static final Duration EXPIRATION_DURATION = Duration.ofMinutes(15);

    public static TwoFASession create(final User user, final String ip) {
        final TwoFASession session = new TwoFASession();
        session.user = user;
        session.ip = ip;
        session.token = PasswordUtil.generateSecureRandomString(255);
        session.expiration = Instant.now().plus(EXPIRATION_DURATION);

        session.persist();

        return session;
    }

    @ManyToOne(optional = false)
    public User user;

    @Column(nullable = false, columnDefinition = "VARCHAR(255)")
    public String token;

    @Column(nullable = false, columnDefinition = "timestamptz")
    public Instant expiration;

    @Column(nullable = false, columnDefinition = "VARCHAR(255)")
    public String ip;

    public boolean isValid() {
        return expiration.isAfter(Instant.now());
    }

}
