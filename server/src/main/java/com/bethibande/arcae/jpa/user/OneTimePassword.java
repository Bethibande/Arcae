package com.bethibande.arcae.jpa.user;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import org.wildfly.security.util.PasswordUtil;

import java.time.Duration;
import java.time.Instant;

@Entity
public class OneTimePassword extends PanacheEntity {

    public static final Duration EXPIRATION_DURATION = Duration.ofMinutes(10);

    public static OneTimePassword generate(final TwoFASession session) {
        final OneTimePassword entity = new OneTimePassword();
        entity.session = session;
        entity.code = PasswordUtil.generateSecureRandomString(6);
        entity.expiration = Instant.now().plus(EXPIRATION_DURATION);

        entity.persist();

        return entity;
    }

    @ManyToOne(optional = false)
    public TwoFASession session;

    @Column(nullable = false, columnDefinition = "VARCHAR(6)")
    public String code;

    @Column(nullable = false, columnDefinition = "timestamptz")
    public Instant expiration;

    public boolean isExpired() {
        return expiration.isBefore(Instant.now());
    }

}
