package com.bethibande.repository.web;

import com.bethibande.repository.jpa.security.AccessToken;
import com.bethibande.repository.jpa.user.User;
import com.bethibande.repository.security.SecurityAttributes;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;

import java.security.Principal;

@RequestScoped
public class AuthenticatedUser {

    @Inject
    protected SecurityIdentity identity;

    public User getSelf() {
        if (identity.isAnonymous()) return null;
        final Principal principal = identity.getPrincipal();
        if (principal instanceof User user) return user;

        return null;
    }

    public AccessToken getAccessToken() {
        if (identity.isAnonymous()) return null;
        return identity.getAttribute(SecurityAttributes.ACCESS_TOKEN);
    }

}
