package com.bethibande.repository.repository.security;

import com.bethibande.repository.jpa.user.User;

import java.util.Optional;

public sealed interface AuthContext permits AbstractAuthContext {

    static AuthContext ofUser(final User user) {
        if (user == null) return new AnonymousAuthContext();
        return new UserAuthContext(user);
    }

    static AuthContext ofSystem() {
        return new SystemAuthContext();
    }

    static AuthContext ofSystem(final AuthContext parent) {
        if (parent instanceof SystemAuthContext) return parent;
        return new SystemAuthContext(parent);
    }

    Optional<AuthContext> parent();

    default boolean isAnonymous() {
        return this instanceof AnonymousAuthContext;
    }

    default boolean isSystem() {
        return this instanceof SystemAuthContext;
    }

    default boolean isUser() {
        return this instanceof UserAuthContext;
    }

}
