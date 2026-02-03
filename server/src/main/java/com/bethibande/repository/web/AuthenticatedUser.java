package com.bethibande.repository.web;

import com.bethibande.repository.jpa.user.User;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;

@RequestScoped
public class AuthenticatedUser {

    @Inject
    protected SecurityIdentity identity;

    public User getSelf() {
        if (identity.isAnonymous()) return null;
        return identity.getPrincipal(User.class);
    }

}
