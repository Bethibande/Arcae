package com.bethibande.repository.repository.security;

import com.bethibande.repository.jpa.user.User;

public final class UserAuthContext extends AbstractAuthContext {

    private final User user;

    UserAuthContext(final AuthContext parent, final User user) {
        super(parent);
        this.user = user;
    }

    UserAuthContext(final User user) {
        this.user = user;
    }

    public User getUser() {
        return user;
    }
}
