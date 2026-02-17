package com.bethibande.repository.security;

import com.bethibande.repository.jpa.security.AccessToken;
import com.bethibande.repository.jpa.user.User;
import com.bethibande.repository.jpa.user.UserRole;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.narayana.jta.TransactionSemantics;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.wildfly.security.util.PasswordUtil;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Startup
@ApplicationScoped
public class SystemAuthentication {

    private static final String SYSTEM_USER_NAME = "system";

    private User user;
    private AccessToken accessToken;

    @PostConstruct
    @Transactional
    public void init() {
        this.user = User.find("name = ?1", SYSTEM_USER_NAME).firstResult();
        if (this.user == null) {
            this.user = new User();
            this.user.name = SYSTEM_USER_NAME;
            this.user.email = "";
            this.user.password = "";
            this.user.roles = List.of(UserRole.ADMIN, UserRole.SYSTEM);
            this.user.persist();
        }

        this.accessToken = AccessToken.find("owner = ?1", user).firstResult();
        if (this.accessToken == null) refreshToken();
    }

    protected  void refreshToken() {
        this.accessToken = new AccessToken();
        this.accessToken.owner = this.user;
        this.accessToken.token = PasswordUtil.generateSecureRandomString(256);
        this.accessToken.expiresAfter = Instant.now().plus(12, ChronoUnit.HOURS);
        this.accessToken.persist();
    }

    public AccessToken getAccessToken() {
        if (this.accessToken.isExpired(Instant.now().minus(10, ChronoUnit.MINUTES))) {
            QuarkusTransaction.runner(TransactionSemantics.JOIN_EXISTING).run(this::refreshToken);
        }
        return this.accessToken;
    }

}
