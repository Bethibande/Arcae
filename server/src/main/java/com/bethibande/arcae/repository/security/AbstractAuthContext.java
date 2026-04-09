package com.bethibande.arcae.repository.security;

import java.util.Optional;

public sealed abstract class AbstractAuthContext implements AuthContext permits AnonymousAuthContext, UserAuthContext, SystemAuthContext {

    protected final AuthContext parent;

    public AbstractAuthContext(final AuthContext parent) {
        this.parent = parent;
    }

    public AbstractAuthContext() {
        this.parent = null;
    }

    @Override
    public Optional<AuthContext> parent() {
        return Optional.ofNullable(this.parent);
    }
}
