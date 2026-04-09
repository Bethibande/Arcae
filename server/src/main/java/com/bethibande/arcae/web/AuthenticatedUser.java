package com.bethibande.arcae.web;

import com.bethibande.arcae.jpa.security.AccessToken;
import com.bethibande.arcae.jpa.security.UserSession;
import com.bethibande.arcae.jpa.user.User;
import com.bethibande.arcae.security.SecurityAttributes;
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

    public UserSession getSession() {
        if (identity.isAnonymous()) return null;
        return identity.getAttribute(SecurityAttributes.SESSION);
    }

    public AccessToken getAccessToken() {
        if (identity.isAnonymous()) return null;
        return identity.getAttribute(SecurityAttributes.ACCESS_TOKEN);
    }

}
