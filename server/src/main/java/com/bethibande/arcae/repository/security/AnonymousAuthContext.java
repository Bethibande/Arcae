package com.bethibande.arcae.repository.security;

public final class AnonymousAuthContext extends AbstractAuthContext {

    AnonymousAuthContext(final AuthContext parent) {
        super(parent);
    }

    AnonymousAuthContext() {
    }
}
