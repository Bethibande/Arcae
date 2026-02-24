package com.bethibande.repository.security;

import com.bethibande.repository.jpa.security.AccessToken;
import com.bethibande.repository.jpa.user.User;
import com.bethibande.repository.jpa.user.UserRole;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.narayana.jta.TransactionSemantics;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.wildfly.security.util.PasswordUtil;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Startup
@ApplicationScoped
public class SystemAuthentication {

    public static final String SYSTEM_USER_NAME = "system";

    private User user;
    private volatile AccessToken accessToken;

    @PostConstruct
    public void init() {
        QuarkusTransaction.runner(TransactionSemantics.REQUIRE_NEW)
                .run(() -> {
                    this.user = User.find("name = ?1", SYSTEM_USER_NAME).firstResult();
                    if (this.user == null) {
                        this.user = new User();
                        this.user.name = SYSTEM_USER_NAME;
                        this.user.email = "";
                        this.user.password = "";
                        this.user.roles = List.of(UserRole.ADMIN, UserRole.SYSTEM);
                        this.user.persist();
                    }

                    refreshToken();
                });
    }

    protected void refreshToken() {
        final Instant now = Instant.now();
        final Instant minAge = now.plus(11, ChronoUnit.MINUTES);
        this.accessToken = AccessToken.find("owner = ?1 AND expiresAfter > ?2", user, minAge).firstResult();
        if (this.accessToken != null && !this.accessToken.isExpired(now)) return;

        this.accessToken = new AccessToken();
        this.accessToken.name = Instant.now().toString() + "-" + PasswordUtil.generateSecureRandomString(4);
        this.accessToken.owner = this.user;
        this.accessToken.token = PasswordUtil.generateSecureRandomString(256);
        this.accessToken.expiresAfter = Instant.now().plus(12, ChronoUnit.HOURS);
        this.accessToken.persist();
    }

    public AccessToken getAccessToken() {
        if (this.accessToken.isExpired(Instant.now().plus(10, ChronoUnit.MINUTES))) {
            QuarkusTransaction.runner(TransactionSemantics.JOIN_EXISTING)
                    .run(this::refreshToken);
        }
        return this.accessToken;
    }

}
