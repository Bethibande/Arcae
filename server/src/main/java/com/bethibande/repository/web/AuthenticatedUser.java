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
        // TODO: Properly cache query result
        return User.find("name = ?1", identity.getPrincipal().getName()).firstResult();
    }

}
